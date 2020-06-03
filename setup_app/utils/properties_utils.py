import os
import sys
import json
import subprocess
import uuid
import glob
import urllib
import ssl

from setup_app import paths
from setup_app.utils import base
from setup_app.static import InstallTypes, colors

from setup_app.config import Config
from setup_app.utils.setup_utils import SetupUtils

from setup_app.pylib.jproperties import Properties

class PropertiesUtils(SetupUtils):

    def getDefaultOption(self, val):
        return 'Yes' if val else 'No'
        

    def getPrompt(self, prompt, defaultValue=None):
        try:
            if defaultValue:
                user_input = input("%s [%s] : " % (prompt, defaultValue)).strip()
                if user_input == '':
                    return defaultValue
                else:
                    return user_input
            else:
                while True:
                    user_input = input("%s : " % prompt).strip()
                    if user_input != '':
                        return user_input

        except KeyboardInterrupt:
            sys.exit()
        except:
            return None

    def check_properties(self):
        self.logIt('Checking properties')
        while not Config.hostname:
            testhost = input('Hostname of this server: ').strip()
            if len(testhost.split('.')) >= 3:
                Config.hostname = testhost
            else:
                print('The hostname has to be at least three domain components. Try again\n')
        while not Config.ip:
            Config.ip = self.get_ip()
        while not Config.orgName:
            Config.orgName = input('Organization Name: ').strip()
        while not Config.countryCode:
            testCode = input('2 Character Country Code: ').strip()
            if len(testCode) == 2:
                Config.countryCode = testCode
            else:
                print('Country code should only be two characters. Try again\n')
        while not Config.city:
            Config.city = input('City: ').strip()
        while not Config.state:
            Config.state = input('State or Province: ').strip()
        if not Config.admin_email:
            tld = None
            try:
                tld = ".".join(self.hostname.split(".")[-2:])
            except:
                tld = Config.hostname
            Config.admin_email = "support@%s" % tld

        if not Config.httpdKeyPass:
            Config.httpdKeyPass = self.getPW()

        if not Config.oxtrust_admin_password and Config.ldapPass:
            Config.oxtrust_admin_password = Config.ldapPass
        
        if not Config.oxtrust_admin_password:
            Config.oxtrust_admin_password = self.getPW()

        if not Config.ldapPass:
            Config.ldapPass = Config.oxtrust_admin_password

        if not Config.opendj_p12_pass:
            Config.opendj_p12_pass = self.getPW()

        if not Config.encode_salt:
            Config.encode_salt = self.getPW() + self.getPW()



        if not Config.idp_client_id:
            Config.idp_client_id = '1101.'+ str(uuid.uuid4())

        if not Config.scim_rs_client_id:
            Config.scim_rs_client_id = '1201.' + str(uuid.uuid4())

        if not Config.scim_rp_client_id:
            Config.scim_rp_client_id = '1202.' + str(uuid.uuid4())

        if not Config.scim_resource_oxid:
            Config.scim_resource_oxid = '1203.' + str(uuid.uuid4())

        if not Config.oxtrust_resource_server_client_id:
            Config.oxtrust_resource_server_client_id = '1401.'  + str(uuid.uuid4())

        if not Config.oxtrust_requesting_party_client_id:
            Config.oxtrust_requesting_party_client_id = '1402.'  + str(uuid.uuid4())

        if not Config.oxtrust_resource_id:
            Config.oxtrust_resource_id = '1403.'  + str(uuid.uuid4())

        if not Config.admin_inum:
            Config.admin_inum = str(uuid.uuid4())

        if not Config.application_max_ram:
            Config.application_max_ram = 3072

        if Config.oxd_server_https:
            Config.templateRenderingDict['oxd_hostname'], Config.templateRenderingDict['oxd_port'] = self.parse_url(Config.oxd_server_https)
            if not Config.templateRenderingDict['oxd_port']: 
                Config.templateRenderingDict['oxd_port'] = 8443
        else:
            Config.templateRenderingDict['oxd_hostname'] = Config.hostname
            Config.oxd_server_https = 'https://{}:8443'.format(Config.hostname)

    def decrypt_properties(self, fn, passwd):
        out_file = fn[:-4] + '.' + uuid.uuid4().hex[:8] + '-DEC~'

        for digest in ('sha256', 'md5'):
            cmd = [paths.cmd_openssl, 'enc', '-md', digest, '-d', '-aes-256-cbc', '-in',  fn, '-out', out_file, '-k', passwd]
            self.logIt('Running: ' + ' '.join(cmd))
            p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            output, err = p.communicate()
            if not err.decode().strip():
                break
        else:
            print("Can't decrypt {} with password {}\n Exiting ...".format(fn, passwd))
            self.run(['rm', '-f', out_file])
            sys.exit(False)

        return out_file

    def load_properties(self, prop_file, no_update=[]):
        self.logIt('Loading Properties %s' % prop_file)

        no_update += ['jre_version', 'node_version', 'jetty_version', 'jython_version', 'jreDestinationPath']

        cb_install = False
        map_db = []

        if prop_file.endswith('.enc'):
            if not Config.properties_password:
                print("setup.properties password was not supplied. Please run with argument -properties-password")
                sys.exit(False)

            prop_file = self.decrypt_properties(prop_file, Config.properties_password)

        try:
            p = base.read_properties_file(prop_file)
        except:
            self.logIt("Error loading properties", True)

        if p.get('ldap_type') == 'openldap':
            self.logIt("ldap_type in setup.properties was changed from openldap to opendj")
            p['ldap_type'] = 'opendj'

        properties_list = list(p.keys())

        for prop in properties_list:
            if prop in no_update:
                continue
            try:
                setattr(Config, prop, p[prop])
                if prop == 'mappingLocations':
                    mappingLocations = json.loads(p[prop])
                    setattr(Config, prop, mappingLocations)
                    for l in mappingLocations:
                        if not mappingLocations[l] in map_db:
                            map_db.append(mappingLocations[l])

                if p[prop] == 'True':
                    setattr(Config, prop, True)
                elif p[prop] == 'False':
                    setattr(Config, prop, False)
            except:
                self.logIt("Error loading property %s" % prop)

        if prop_file.endswith('-DEC~'):
            self.run(['rm', '-f', prop_file])

        if not 'oxtrust_admin_password' in properties_list:
            Config.oxtrust_admin_password = p['ldapPass']
            
        if p.get('ldap_hostname') != 'localhost':
            if p.get('remoteLdap','').lower() == 'true':
                Config.wrends_install = InstallTypes.REMOTE
            elif p.get('installLdap','').lower() == 'true':
                Config.wrends_install = InstallTypes.LOCAL
            elif p.get('wrends_install'):
                Config.wrends_install = p['wrends_install']   
            else:
                Config.wrends_install = InstallTypes.NONE

        if map_db and not 'ldap' in map_db:
            Config.wrends_install = InstallTypes.NONE

        if 'couchbase' in map_db:
            if 'remoteCouchbase' in properties_list and p.get('remoteCouchbase','').lower() == 'true':
                Config.cb_install = InstallTypes.REMOTE
            elif p.get('cb_install'):
                Config.cb_install = p['cb_install']
            elif 'persistence_type' in properties_list and p.get('persistence_type') in ('couchbase', 'hybrid'):
                Config.cb_install = InstallTypes.LOCAL
            else:
                Config.cb_install = InstallTypes.NONE

        if Config.cb_install == InstallTypes.LOCAL:
            available_backends = self.getBackendTypes()
            if not 'couchbase' in available_backends:
                print("Couchbase package is not available exiting.")
                sys.exit(1)


        if (not 'cb_password' in properties_list) and Config.cb_install:
            Config.cb_password = p.get('ldapPass')

        if Config.cb_install == InstallTypes.REMOTE:
            cbm_ = CBM(Config.couchbase_hostname, Config.couchebaseClusterAdmin, Config.cb_password)
            if not cbm_.test_connection().ok:
                print("Can't connect to remote Couchbase Server with credentials found in setup.properties.")
                sys.exit(1)

        if Config.wrends_install == InstallTypes.REMOTE:
            conn_check = self.check_remote_ldap(Config.ldap_hostname, Config.ldap_binddn, Config.ldapPass)
            if not conn_check['result']:
                print("Can't connect to remote LDAP Server with credentials found in setup.properties.")
                sys.exit(1)

        for si, se in ( 
                        ('installPassport', 'gluuPassportEnabled'),
                        ('gluuRadiusEnabled', 'gluuRadiusEnabled'),
                        ('installSaml', 'gluuSamlEnabled'),
                        ):
            if getattr(Config, si):
                setattr(Config, se, 'true')

        if not 'oxtrust_admin_password' in p:
            p['oxtrust_admin_password'] = p['ldapPass']


        return p

    def save_properties(self, prop_fn=None, obj=None):
        
        if not prop_fn:
            prop_fn = Config.savedProperties
            
        if not obj:
            obj = self

        self.logIt('Saving properties to %s' % prop_fn)
        
        def getString(value):
            if isinstance(value, str):
                return value.strip()
            elif isinstance(value, bool) or isinstance(value, int) or isinstance(value, float):
                return str(value)
            else:
                return ''

        try:
            p = Properties()
            keys = list(Config.__dict__.keys())
            keys.sort()
            for key in keys:
                key = str(key)
                if key in ('couchbaseInstallOutput', 'post_messages', 'cb_bucket_roles', 'properties_password', 'non_setup_properties'):
                    continue
                if key.startswith('cmd_'):
                    continue
                if key == 'mappingLocations':
                    p[key] = json.dumps(Config.__dict__[key])
                else:
                    value = getString(Config.__dict__[key])
                    if value != '':
                        p[key] = value

            with open(prop_fn, 'wb') as f:
                p.store(f, encoding="utf-8")
            
            self.run([paths.cmd_openssl, 'enc', '-aes-256-cbc', '-in', prop_fn, '-out', prop_fn+'.enc', '-k', Config.oxtrust_admin_password])
            
            Config.post_messages.append(
                "Encrypted properties file saved to {0}.enc with password {1}\nDecrypt the file with the following command if you want to re-use:\nopenssl enc -d -aes-256-cbc -in {2}.enc -out {3}".format(
                prop_fn,  Config.oxtrust_admin_password, os.path.basename(prop_fn), os.path.basename(Config.setup_properties_fn)))
            
            self.run(['rm', '-f', prop_fn])
            
        except:
            self.logIt("Error saving properties", True)

    def getBackendTypes(self):

        backend_types = []

        if glob.glob(Config.distFolder+'/app/opendj-server-*4*.zip'):
            backend_types.append('wrends')

        if glob.glob(Config.distFolder+'/couchbase/couchbase-server-enterprise*.' + base.clone_type):
            backend_types.append('couchbase')

        return backend_types



    def test_cb_servers(self, couchbase_hostname):
        cb_hosts = re_split_host.findall(couchbase_hostname)

        cb_query_node = None
        retval = {'result': True, 'query_node': cb_query_node, 'reason': ''}

        for i, cb_host in enumerate(cb_hosts):

                cbm_ = CBM(cb_host, Config.couchebaseClusterAdmin, Config.cb_password)
                if not Config.thread_queue:
                    print("    Checking Couchbase connection for " + cb_host)

                cbm_result = cbm_.test_connection()
                if not cbm_result.ok:
                    if not Config.thread_queue:
                        print("    Can't establish connection to Couchbase server with given parameters.")
                        print("**", cbm_result.reason)
                    retval['result'] = False
                    retval['reason'] = cb_host + ': ' + cbm_result.reason
                    return retval
                try:
                    qr = cbm_.exec_query('select * from system:indexes limit 1')
                    if qr.ok:
                        cb_query_node = i
                except:
                    pass
        else:

            if cbm_result.ok and cb_query_node != None:
                if not Config.thread_queue:
                    print("    Successfully connected to Couchbase server")
                cb_host_ = cb_hosts[self.cb_query_node]
                return retval
            if cb_query_node == None:
                if not Config.thread_queue:
                    print("Can't find any query node")
                retval['result'] = False
                retval['reason'] = "Can't find any query node"

        return retval

    def prompt_remote_couchbase(self):
    
        while True:
            Config.couchbase_hostname = self.getPrompt("    Couchbase hosts")
            Config.couchebaseClusterAdmin = self.getPrompt("    Couchbase User")
            Config.cb_password =self.getPrompt("    Couchbase Password")

            result = self.test_cb_servers(Config.couchbase_hostname)

            if result['result']:
                self.cb_query_node = result['query_node']
                break

    def check_remote_ldap(self, ldap_host, ldap_binddn, ldap_password):
        
        result = {'result': True, 'reason': ''}
        
        ldap_server = Server(ldap_host, port=int(Config.ldaps_port), use_ssl=True)
        conn = Connection(
            ldap_server,
            user=ldap_binddn,
            password=ldap_password,
            )

        try:
            conn.bind()
        except Exception as e:
            result['result'] = False
            result['reason'] = str(e)
        
        return result

    def check_oxd_server(self, oxd_url, error_out=True, log_error=True):

        oxd_url = os.path.join(oxd_url, 'health-check')
        try:
            result = urllib.request.urlopen(
                        oxd_url,
                        timeout = 2,
                        context=ssl._create_unverified_context()
                    )
            if result.code == 200:
                oxd_status = json.loads(result.read().decode())
                if oxd_status['status'] == 'running':
                    return True
        except Exception as e:
            if log_error:
                if Config.thread_queue:
                    return str(e)
                if error_out:
                    print(colors.DANGER)
                    print("Can't connect to oxd-server with url {}".format(oxd_url))
                    print("Reason: ", e)
                    print(colors.ENDC)

    def check_oxd_ssl_cert(self, oxd_hostname, oxd_port):

        oxd_cert = ssl.get_server_certificate((oxd_hostname, oxd_port))
        oxd_crt_fn = '/tmp/oxd_{}.crt'.format(str(uuid.uuid4()))
        self.writeFile(oxd_crt_fn, oxd_cert)
        ssl_subjects = self.get_ssl_subject(oxd_crt_fn)
        
        if ssl_subjects['CN'] != oxd_hostname:
            return ssl_subjects

    def add_couchbase_post_messages(self):
        self.post_messages.append( 
                "Please note that you have to update your firewall configuration to\n"
                "allow connections to the following ports on Couchbase Server:\n"
                "4369, 28091 to 28094, 9100 to 9105, 9998, 9999, 11207, 11209 to 11211,\n"
                "11214, 11215, 18091 to 18093, and from 21100 to 21299."
            )
        (w, e) = ('', '') if Config.thread_queue else (gluu_utils.colors.WARNING, gluu_utils.colors.ENDC)
        self.post_messages.append(
            w+"By using Couchbase Server you agree to the End User License Agreement.\n"
            "See /opt/couchbase/LICENSE.txt"+e
            )

    def promptForBackendMappings(self):

        options = []
        options_text = []
        
        bucket_list = list(self.couchbaseBucketDict.keys())

        for i, m in enumerate(bucket_list):
            options_text.append('({0}) {1}'.format(i+1,m))
            options.append(str(i+1))

        options_text = 'Use WrenDS to store {}'.format(' '.join(options_text))

        re_pattern = '^[1-{0}]+$'.format(len(self.couchbaseBucketDict))

        while True:
            prompt = self.getPrompt(options_text)
            if re.match(re_pattern, prompt):
                break
            else:
                print("Please select one of {0}.".format(", ".join(options)))

        couchbase_mappings = bucket_list[:]

        for i in prompt:
            m = bucket_list[int(i)-1]
            couchbase_mappings.remove(m)

        for m in couchbase_mappings:
            self.mappingLocations[m] = 'couchbase'


    def promptForCasaInstallation(self, promptForCasa='n'):
        
        if promptForCasa == 'n':
            promptForCasa = self.getPrompt("Install Casa?", 
                                            self.getDefaultOption(Config.installCasa)
                                            )[0].lower()
        Config.installCasa = True if promptForCasa == 'y' else False

        if Config.installCasa:
            print ("Please enter URL of oxd-server if you have one, for example: https://oxd.mygluu.org:8443")
            if Config.oxd_package:
                print ("Else leave blank to install oxd server locally.")

            while True:
                oxd_server_https = input("oxd Server URL: ").lower()
                
                if (not oxd_server_https) and Config.oxd_package:
                    Config.installOxd = True
                    break

                print ("Checking oxd server ...")
                if self.check_oxd_server(oxd_server_https):
                    oxd_hostname, oxd_port = self.parse_url(oxd_server_https)
                    oxd_cert = ssl.get_server_certificate((oxd_hostname, oxd_port))
                    oxd_crt_fn = '/tmp/oxd_{}.crt'.format(str(uuid.uuid4()))
                    self.writeFile(oxd_crt_fn, oxd_cert)
                    ssl_subjects = self.get_ssl_subject(oxd_crt_fn)
                    
                    if not ssl_subjects['CN'] == oxd_hostname:
                        print (('Hostname of oxd ssl certificate is {0}{1}{2} '
                                'which does not match {0}{3}{2}, \ncasa won\'t start '
                                'properly').format(
                                        colors.DANGER,
                                        ssl_subjects['CN'],
                                        colors.ENDC,
                                        oxd_hostname
                                        ))
                    else:
                        Config.oxd_server_https = oxd_server_https
                        break

    def promptForProperties(self):

        if Config.noPrompt:
            return

        promptForMITLicense = self.getPrompt("Do you acknowledge that use of the Gluu Server is under the Apache-2.0 license?", "N|y")[0].lower()
        if promptForMITLicense != 'y':
            sys.exit(0)

        # IP address needed only for Apache2 and hosts file update
        if Config.installHttpd:
            Config.ip = self.get_ip()

        detectedHostname = self.detect_hostname()

        if detectedHostname == 'localhost':
            detectedHostname = None

        while True:
            if detectedHostname:
                Config.hostname = self.getPrompt("Enter hostname", detectedHostname)
            else:
                Config.hostname = self.getPrompt("Enter hostname")

            if Config.hostname != 'localhost':
                break
            else:
                print("Hostname can't be \033[;1mlocalhost\033[0;0m")

        Config.oxd_server_https = 'https://{}:8443'.format(Config.hostname)

        # Get city and state|province code
        Config.city = self.getPrompt("Enter your city or locality", Config.city)
        Config.state = self.getPrompt("Enter your state or province two letter code", Config.state)

        # Get the Country Code
        long_enough = False
        while not long_enough:
            countryCode = self.getPrompt("Enter two letter Country Code", Config.countryCode)
            if len(countryCode) != 2:
                print("Country code must be two characters")
            else:
                Config.countryCode = countryCode
                long_enough = True

        Config.orgName = self.getPrompt("Enter Organization Name", Config.orgName)

        while True:
            Config.admin_email = self.getPrompt('Enter email address for support at your organization', Config.admin_email)
            if self.check_email(Config.admin_email):
                break
            else:
                print("Please enter valid email address")
        
        Config.application_max_ram = self.getPrompt("Enter maximum RAM for applications in MB", str(Config.application_max_ram))

        oxtrust_admin_password = Config.oxtrust_admin_password if Config.oxtrust_admin_password else self.getPW(special='.*=!%&+/-')

        while True:
            oxtrust_admin_password = self.getPrompt("Enter oxTrust Admin Password", oxtrust_admin_password)
            if len(oxtrust_admin_password) > 5:
                break
            else:
                print("Password must be at least 6 characters")
        
        Config.oxtrust_admin_password = oxtrust_admin_password

        available_backends = self.getBackendTypes()

        localWrendsOnly = False

        if (Config.wrends_install != InstallTypes.REMOTE) and (not Config.cb_install) and (available_backends == ['wrends']):
            Config.wrends_install = InstallTypes.LOCAL
            
        elif Config.wrends_install != InstallTypes.REMOTE and (Config.cb_install == InstallTypes.REMOTE or 'couchbase' in available_backends):
            promptForLDAP = self.getPrompt("Install Local WrenDS Server?", "Yes")[0].lower()
            if promptForLDAP[0] == 'y':
                Config.wrends_install = InstallTypes.LOCAL
            else:
                Config.wrends_install = NONE

        if Config.wrends_install == InstallTypes.LOCAL:

            ldapPass = Config.ldapPass if Config.ldapPass else Config.oxtrust_admin_password

            while True:
                ldapPass = self.getPrompt("Enter Password for LDAP Admin ({})".format(Config.ldap_binddn), Config.oxtrust_admin_password)

                if self.checkPassword(ldapPass):
                    break
                else:
                    print("Password must be at least 6 characters and include one uppercase letter, one lowercase letter, one digit, and one special character.")

            Config.ldapPass = ldapPass

        elif Config.wrends_install == InstallTypes.REMOTE:
            while True:
                ldapHost = self.getPrompt("    LDAP hostname")
                ldapPass = self.getPrompt("    Password for '{0}'".format(Config.ldap_binddn))
                conn_check = self.check_remote_ldap(ldapHost, Config.ldap_binddn, ldapPass)
                if conn_check['result']:
                    break
                else:
                    print("    {}Error connecting to LDAP server: {} {}".format(colors.FAIL, conn_check['reason'], colors.ENDC))

            Config.ldapPass = ldapPass
            Config.ldap_hostname = ldapHost

        if Config.cb_install == InstallTypes.REMOTE:
            self.prompt_remote_couchbase()

        elif 'couchbase' in available_backends:
            promptForCB = self.getPrompt("Install Local Couchbase Server?", "Yes")[0].lower()
            if promptForCB[0] == 'y':
                self.cb_install = InstallTypes.LOCAL
                self.isCouchbaseUserAdmin = True

                while True:
                    cbPass = self.getPrompt("Enter Password for Couchbase {}admin{} user".format(colors.BOLD, colors.ENDC), Config.oxtrust_admin_password)

                    if self.checkPassword(cbPass):
                        break
                    else:
                        print("Password must be at least 6 characters and include one uppercase letter, one lowercase letter, one digit, and one special character.")

                Config.cb_password = cbPass

        if not (Config.wrends_install or Config.cb_install):
            print("{}You must have at least one DB backend. Exiting...{}".format(colors.WARNING, colors.ENDC))
            sys.exit(False)

        if Config.cb_install:
            Config.cache_provider_type = 'NATIVE_PERSISTENCE'
            self.add_couchbase_post_messages()

        if not Config.wrends_install and Config.cb_install:
            Config.mappingLocations = { group: 'couchbase' for group in Config.couchbaseBucketDict }
            Config.persistence_type = 'couchbase'

        elif Config.wrends_install and Config.cb_install:
            Config.promptForBackendMappings()
            Config.persistence_type = 'hybrid'

        if Config.allowPreReleasedFeatures:
            while True:
                java_type = self.getPrompt("Select Java type: 1.Jre-1.8   2.OpenJDK-11", '1')
                if not java_type:
                    java_type = 1
                    break
                if java_type in '12':
                    break
                else:
                    print("Please enter 1 or 2")

            if java_type == '1':
                Config.java_type = 'jre'
            else:
                Config.java_type = 'jdk'
                Config.defaultTrustStoreFN = '%s/lib/security/cacerts' % Config.jre_home
                
        promptForOxAuth = self.getPrompt("Install oxAuth OAuth2 Authorization Server?", 
                                        self.getDefaultOption(Config.installOxAuth)
                                            )[0].lower()
        self.installOxAuth = True if promptForOxAuth == 'y' else False

        promptForOxTrust = self.getPrompt("Install oxTrust Admin UI?",
                                            self.getDefaultOption(Config.installOxTrust)
                                            )[0].lower()
        Config.installOxTrust = True if promptForOxTrust == 'y' else False

        couchbase_mappings_ = self.getMappingType('couchbase')
        buckets_ = [ 'gluu_{}'.format(b) for b in couchbase_mappings_ ]

        buckets_.append('gluu')

        if Config.cb_install == InstallTypes.REMOTE:

            isCBRoleOK = self.checkCBRoles(buckets_)

            if not isCBRoleOK[0]:
                print("{}Please check user {} has roles {} on bucket(s) {}{}".format(
                                colors.DANGER,
                                self.cbm.auth.username,
                                ', '.join(self.cb_bucket_roles),
                                ', '.join(isCBRoleOK[1]),
                                colors.ENDC
                                ))
                sys.exit(False)



        promptForHTTPD = self.getPrompt("Install Apache HTTPD Server", 
                                        self.getDefaultOption(Config.installHTTPD)
                                        )[0].lower()
        Config.installHttpd = True if promptForHTTPD == 'y' else False

        promptForScimServer = self.getPrompt("Install Scim Server?",
                                            self.getDefaultOption(Config.installScimServer)
                                            )[0].lower()
        Config.installScimServer = True if promptForScimServer == 'y' else False
            

        promptForFido2Server = self.getPrompt("Install Fido2 Server?",
                                            self.getDefaultOption(Config.installFido2)
                                            )[0].lower()
        Config.installFido2 = True if promptForFido2Server == 'y' else False


        promptForShibIDP = self.getPrompt("Install Shibboleth SAML IDP?",
                                            self.getDefaultOption(Config.installSaml)
                                            )[0].lower()
        if promptForShibIDP == 'y':
            Config.shibboleth_version = 'v3'
            Config.installSaml = True
            Config.gluuSamlEnabled = 'true'
            if Config.persistence_type in ('couchbase','hybrid'):
                Config.couchbaseShibUserPassword = self.getPW()
        else:
            Config.installSaml = False

        promptForOxAuthRP = self.getPrompt("Install oxAuth RP?",
                                            self.getDefaultOption(Config.installOxAuthRP)
                                            )[0].lower()
        Config.installOxAuthRP = True if promptForOxAuthRP == 'y'else False

        promptForPassport = self.getPrompt("Install Passport?", 
                                            self.getDefaultOption(Config.installPassport)
                                            )[0].lower()
        Config.installPassport = True if promptForPassport == 'y' else False

        if os.path.exists(os.path.join(Config.distGluuFolder, 'casa.war')):
            self.promptForCasaInstallation()

        if (not Config.installOxd) and Config.oxd_package:
            promptForOxd = self.getPrompt("Install Oxd?", 
                                                self.getDefaultOption(Config.installOxd)
                                                )[0].lower()
            Config.installOxd = True if promptForOxd == 'y' else False


        if Config.installOxd:
            promptForOxdGluuStorage = self.getPrompt("  Use Gluu Storage for Oxd?",
                                                self.getDefaultOption(Config.oxd_use_gluu_storage)
                                                )[0].lower()
            Config.oxd_use_gluu_storage = True if promptForOxdGluuStorage == 'y' else False


        promptForGluuRadius = self.getPrompt("Install Gluu Radius?", 
                                            self.getDefaultOption(Config.installGluuRadius)
                                            )[0].lower()
        Config.installGluuRadius = True if promptForGluuRadius == 'y' else False
