#!/usr/bin/python
import os
import argparse
import sys
import subprocess
import json

if not os.path.exists('setup.py'):
    print "This script should be run from /install/community-edition-setup/"
    sys.exit()
    
if not os.path.exists('/install/community-edition-setup/setup.properties.last'):
    print "setup.properties.last is missing can't continue"
    sys.exit()

f=open('setup.py').readlines()

for l in f:
    if l.startswith('from pyDes import *'):
        break
else:
    f.insert(1, 'from pyDes import *\n')
    with open('setup.py','w') as w:
        w.write(''.join(f))

parser = argparse.ArgumentParser()
parser.add_argument("-addshib", help="Install Shibboleth SAML IDP", action="store_true")
parser.add_argument("-addpassport", help="Install Passport", action="store_true")
args = parser.parse_args()

if  len(sys.argv)<2:
    parser.print_help()
    parser.exit(1)


from setup import Setup

setupObj = Setup('.')

setupObj.setup = setupObj

setupObj.load_properties('/install/community-edition-setup/setup.properties.last')

if setupObj.ldap_type == 'opendj':
    setupObj.ldapCertFn = setupObj.opendj_cert_fn
else:
    setupObj.ldapCertFn = setupObj.openldapTLSCert

setupObj.ldapCertFn = setupObj.opendj_cert_fn

def installSaml():

    metadata_file = os.path.join(setupObj.idp3MetadataFolder, setupObj.idp3_metadata)

    if os.path.exists(metadata_file):
        print "Shibboleth is already installed on this system"
        return

    print "Installing Shibboleth ..."
    setupObj.oxTrustConfigGeneration = "true"
    setupObj.calculate_selected_aplications_memory()
    realIdp3Folder = os.path.realpath(setupObj.idp3Folder)
    setupObj.run([setupObj.cmd_chown, '-R', 'jetty:jetty', realIdp3Folder])
    realIdp3BinFolder = "%s/bin" % realIdp3Folder
    if os.path.exists(realIdp3BinFolder):
        setupObj.run(['find', realIdp3BinFolder, '-name', '*.sh', '-exec', 'chmod', "755", '{}',  ';'])
    
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3Folder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3MetadataFolder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3MetadataCredentialsFolder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3LogsFolder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3LibFolder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3ConfFolder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3ConfAuthnFolder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3CredentialsFolder])
    setupObj.run([setupObj.cmd_mkdir, '-p', setupObj.idp3WebappFolder])
    

    setupObj.run(['/usr/bin/wget', setupObj.idp3_war, '--no-verbose', '-c', '--retry-connrefused', '--tries=10', '-O', '%s/idp.war' % setupObj.distGluuFolder])
    setupObj.run(['/usr/bin/wget', setupObj.idp3_cml_keygenerator, '--no-verbose', '-c', '--retry-connrefused', '--tries=10', '-O', setupObj.distGluuFolder + '/idp3_cml_keygenerator.jar'])
    setupObj.run(['/usr/bin/wget', setupObj.idp3_dist_jar, '--no-verbose', '-c', '--retry-connrefused', '--tries=10', '-O', setupObj.distGluuFolder + '/shibboleth-idp.jar'])
    setupObj.installSaml = True
    setupObj.install_saml()
    
    
    
    metadata = open(metadata_file).read()
    metadata = metadata.replace('md:ArtifactResolutionService', 'ArtifactResolutionService')
    with open(metadata_file,'w') as F:
        F.write(metadata)
    
    setupObj.run([setupObj.cmd_chown, '-R', 'jetty:jetty', setupObj.idp3Folder])
    print "Shibboleth installation done"


def installPassport():
    print "Installing Passport ..."
    proc = subprocess.Popen('echo "" | /opt/jre/bin/keytool -list -v -keystore /etc/certs/passport-rp.jks', shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    alias_l=''

    jks = {'keys': []}

    for l in proc.stdout.readlines():
        if l.startswith('Alias name:'):
            alias_l = l
        if 'SHA512withRSA' in l:
            alias = alias_l[11:].strip()
            jks['keys'].append({'kid': alias, 'alg': 'RS512'})
            break

    setupObj.passport_rp_client_jwks = [json.dumps(jks)]
    
    setupObj.install_passport()
    print "Passport installation done"


if args.addshib:
    installSaml()

if args.addpassport:
    installPassport()

print "Please exit container and restart Gluu Server"
