#!/usr/bin/python

import time
import traceback
import sys
import os
import shutil
import hashlib

# Unix commands
mkdir = '/bin/mkdir'
cat = '/bin/cat'
hostname = '/bin/hostname'
grep = '/bin/grep'
ldapsearch = "/opt/opendj/bin/ldapsearch"
unzip = "/usr/bin/unzip"
find = "/usr/bin/find"
mkdir = "/bin/mkdir"

# File system stuff
oxauth_war = "/opt/tomcat/webapps/oxauth.war"
oxtrust_war = "/opt/tomcat/webapps/identity.war"
oxauth_original_dir = "/tmp/oxauth-original"
oxtrust_original_dir = "/tmp/oxtrust-original"
oxauth_modified_dir = "/opt/tomcat/webapps/oxauth"
oxtrust_modified_dir = "/opt/tomcat/webapps/identity"
log = "./export_24.log"
logError = "./export_24.error"
bu_folder = "./backup_24"
propertiesFn = "%s/setup.properties" % bu_folder
folders_to_backup = ['/opt/tomcat/conf',
                     '/opt/tomcat/endorsed',
                     '/opt/opendj/config',
                     '/etc/certs',
                     '/opt/idp/conf',
                     '/opt/idp/metadata']

# LDAP Stuff
ldap_pass = None
ldap_creds = None
base_dns = ['ou=people',
            'ou=groups',
            'ou=attributes',
            'ou=scopes',
            'ou=clients',
            'ou=scripts',
            'ou=uma',
            'ou=hosts',
            'ou=u2f']


def backupCustomizations():
    dirs = [oxauth_original_dir, oxtrust_original_dir]
    for dir in dirs:
        if not os.path.exists(dir):
            os.mkdir(dir)
    output = runCommand([unzip, oxauth_war, '-d', oxauth_original_dir])
    output = runCommand([unzip, oxtrust_war, '-d', oxtrust_original_dir])
    logIt(output)
    dirs = [(oxauth_modified_dir, oxauth_original_dir),
            (oxtrust_modified_dir, oxtrust_original_dir)]
    for dir_tup in dirs:
        modified_dir = dir_tup[0]
        original_dir = dir_tup[1]
        files = runCommand([find, modified_dir], True)
        for modified_file in files:
            modified_file = modified_file.strip()
            original_file = modified_file.replace(modified_dir, original_dir)
            if not os.path.isdir(modified_file):
                if not os.path.exists(original_file):
                    logIt("Found new file: %s" % modified_file)
                    copyFile(modified_file, bu_folder)
                else:
                    modified_hash = hash_file(modified_file)
                    original_hash = hash_file(original_file)
                    if not modified_hash == original_hash:
                        logIt("Found changed file: %s" % modified_file)
                        copyFile(modified_file, bu_folder)
    shutil.rmtree(oxauth_original_dir)
    shutil.rmtree(oxtrust_original_dir)


def backupFiles():
    for folder in folders_to_backup:
        try:
            shutil.copytree(folder, bu_folder + folder)
        except:
            logIt("Failed to backup %s" % folder)


def clean(s):
    return s.replace('@', '').replace('!', '').replace('.', '')


def copyFile(fn, dir):
    parent_Dir = os.path.split(fn)[0]
    bu_dir = "%s/%s" % (bu_folder, parent_Dir)
    if not os.path.exists(bu_dir):
        runCommand([mkdir, "-p", bu_dir])
    bu_fn = os.path.join(bu_dir, os.path.split(fn)[-1])
    shutil.copyfile(fn, bu_fn)


def getOrgInum():
    args = [ldapsearch] + ldap_creds + ['-s', 'one', '-b', 'o=gluu',
                                        'o=*', 'dn']
    output = runCommand(args)
    return output.split(",")[0].split("o=")[-1]


def getLdif():
    orgInum = getOrgInum()
    # Backup the data
    for basedn in base_dns:
        args = [ldapsearch] + ldap_creds + [
            '-b', '%s,o=%s,o=gluu' % (basedn, orgInum), 'objectclass=*']
        output = runCommand(args)
        ou = basedn.split("=")[-1]
        f = open("%s/ldif/%s.ldif" % (bu_folder, ou), 'w')
        f.write(output)
        f.close()

    # Backup the appliance config
    args = [ldapsearch] + ldap_creds + \
           ['-b',
            'ou=appliances,o=gluu',
            '-s',
            'one',
            'objectclass=*']
    output = runCommand(args)
    f = open("%s/ldif/appliance.ldif" % bu_folder, 'w')
    f.write(output)
    f.close()

    # Backup the oxtrust config
    args = [ldapsearch] + ldap_creds + \
           ['-b',
            'ou=appliances,o=gluu',
            'objectclass=oxTrustConfiguration']
    output = runCommand(args)
    f = open("%s/ldif/oxtrust_config.ldif" % bu_folder, 'w')
    f.write(output)
    f.close()

    # Backup the oxauth config
    args = [ldapsearch] + ldap_creds + \
           ['-b',
            'ou=appliances,o=gluu',
            'objectclass=oxAuthConfiguration']
    output = runCommand(args)
    f = open("%s/ldif/oxauth_config.ldif" % bu_folder, 'w')
    f.write(output)
    f.close()

    # Backup the trust relationships
    args = [ldapsearch] + ldap_creds + ['-b', 'ou=appliances,o=gluu',
                                        'objectclass=gluuSAMLconfig']
    output = runCommand(args)
    f = open("%s/ldif/trust_relationships.ldif" % bu_folder, 'w')
    f.write(output)
    f.close()

    # Backup the org
    args = [ldapsearch] + ldap_creds + ['-s', 'base', '-b',
                                        'o=%s,o=gluu' % orgInum,
                                        'objectclass=*']
    output = runCommand(args)
    f = open("%s/ldif/organization.ldif" % bu_folder, 'w')
    f.write(output)
    f.close()

    # Backup o=site
    args = [ldapsearch] + ldap_creds + ['-b', 'o=site', 'objectclass=*']
    output = runCommand(args)
    f = open("%s/ldif/site.ldif" % bu_folder, 'w')
    f.write(output)
    f.close()


def runCommand(args, return_list=False):
        try:
            logIt("Running command : %s" % " ".join(args))
            output = None
            if return_list:
                output = os.popen(" ".join(args)).readlines()
            else:
                output = os.popen(" ".join(args)).read().strip()
            return output
        except:
            logIt("Error running command : %s" % " ".join(args), True)
            logIt(traceback.format_exc(), True)
            sys.exit(1)


def genProperties():
    props = {}
    props['ldapPass'] = runCommand([cat, password_file])
    props['hostname'] = runCommand([hostname])
    props['inumAppliance'] = runCommand(
        [grep, "^inum", "%s/ldif/appliance.ldif" % bu_folder]
    ).split("\n")[0].split(":")[-1].strip()
    props['inumApplianceFN'] = clean(props['inumAppliance'])
    props['inumOrg'] = getOrgInum()
    props['inumOrgFN'] = clean(props['inumOrg'])
    props['baseInum'] = props['inumOrg'][:21]
    props['encode_salt'] = runCommand(
        [cat, "%s/opt/tomcat/conf/salt" % bu_folder]).split("=")[-1].strip()
    f = open(propertiesFn, 'a')
    for key in props.keys():
        f.write("%s=%s\n" % (key, props[key]))
    f.close()


def hash_file(filename):
    # From http://www.programiz.com/python-programming/examples/hash-file
    h = hashlib.sha1()
    with open(filename, 'rb') as file:
        chunk = 0
        while chunk != b'':
            chunk = file.read(1024)
            h.update(chunk)
    return h.hexdigest()


def logIt(msg, errorLog=False):
    if errorLog:
        f = open(logError, 'a')
        f.write('%s %s\n' % (time.strftime('%X %x'), msg))
        f.close()
    f = open(log, 'a')
    f.write('%s %s\n' % (time.strftime('%X %x'), msg))
    f.close()


def makeFolders():
    folders = [bu_folder, "%s/ldif" % bu_folder]
    for folder in folders:
        try:
            if not os.path.exists(folder):
                runCommand([mkdir, '-p', folder])
        except:
            logIt("Error making folders", True)
            logIt(traceback.format_exc(), True)
            sys.exit(3)


def main():
    global ldap_pass
    global ldap_creds

    ldap_pass = raw_input("Enter your LDAP password: ")
    ldap_creds = ['-h', 'localhost', '-p', '1389', '-D',
                  '"cn=directory', 'manager"', '-w', ldap_pass]
    makeFolders()
    backupFiles()
    getLdif()
    genProperties()
    backupCustomizations()

if __name__ == "__main__":
    main()
