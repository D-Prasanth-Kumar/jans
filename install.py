#!/usr/bin/python3

import sys
import os
import argparse
import zipfile
import shutil
import time

from urllib.request import urlretrieve
from urllib.parse import urljoin


setup_package_name = 'master.zip'
maven_base_url = 'https://ox.gluu.org/maven/org/gluu/'

app_versions = {
  "JANS_APP_VERSION": "5.0.0",
  "JANS_BUILD": "-SNAPSHOT", 
  "JETTY_VERSION": "9.4.31.v20200723", 
  "AMAZON_CORRETTO_VERSION": "11.0.8.10.1", 
  "JYTHON_VERSION": "2.7.2",
  "OPENDJ_VERSION": "4.0.0.gluu",
  "SETUP_BRANCH": "master",
}

jans_dir = '/opt/jans'
app_dir = '/opt/dist/app'
jans_app_dir = '/opt/dist/jans'
scripts_dir = '/opt/dist/scripts'
setup_dir = os.path.join(jans_dir, 'jans-setup')

for d in (jans_dir, app_dir, jans_app_dir, scripts_dir):
    if not os.path.exists(d):
        os.makedirs(d)

parser = argparse.ArgumentParser(description="This script downloads Janssen Server components and fires setup")
parser.add_argument('-u', help="Use downloaded components", action='store_true')
parser.add_argument('--args', help="Arguments to be passed to setup.py")
argsp = parser.parse_args()


def download(url, target_fn):
    dst = os.path.join(app_dir, target_fn)
    pardir, fn = os.path.split(dst)
    if not os.path.exists(pardir):
        os.makedirs(pardir)
    print("Downloading", url, "to", dst)
    urlretrieve(url, dst)


setup_zip_file = os.path.join(jans_app_dir, 'jans-setup.zip')

if not argsp.u:
    setup_url = 'https://github.com/JanssenProject/jans-setup/archive/master.zip'
    download(setup_url, setup_zip_file)

    download('https://corretto.aws/downloads/resources/{0}/amazon-corretto-{0}-linux-x64.tar.gz'.format(app_versions['AMAZON_CORRETTO_VERSION']), os.path.join(app_dir, 'amazon-corretto-{0}-linux-x64.tar.gz'.format(app_versions['AMAZON_CORRETTO_VERSION'])))
    download('https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/{0}/jetty-distribution-{0}.tar.gz'.format(app_versions['JETTY_VERSION']), os.path.join(app_dir,'jetty-distribution-{0}.tar.gz'.format(app_versions['JETTY_VERSION'])))
    download('https://repo1.maven.org/maven2/org/python/jython-installer/{0}/jython-installer-{0}.jar'.format(app_versions['JYTHON_VERSION']), os.path.join(app_dir, 'jython-installer-{0}.jar'.format(app_versions['JYTHON_VERSION'])))
    download('https://ox.gluu.org/maven/org/gluufederation/opendj/opendj-server-legacy/{0}/opendj-server-legacy-{0}.zip'.format(app_versions['OPENDJ_VERSION']), os.path.join(app_dir, 'opendj-server-legacy-{0}.zip'.format(app_versions['OPENDJ_VERSION'])))
    
    download(urljoin(maven_base_url, 'oxauth-server/{0}{1}/oxauth-server-{0}{1}.war'.format(app_versions['JANS_APP_VERSION'], app_versions['JANS_BUILD'])), os.path.join(jans_app_dir, 'oxauth.war'))
    download(urljoin(maven_base_url, 'oxauth-client/{0}{1}/oxauth-client-{0}{1}-jar-with-dependencies.jar'.format(app_versions['JANS_APP_VERSION'], app_versions['JANS_BUILD'])), os.path.join(jans_app_dir, 'oxauth-client-jar-with-dependencies.jar'))
    download(urljoin(maven_base_url, 'scim-server/{0}{1}/scim-server-{0}{1}.war'.format(app_versions['JANS_APP_VERSION'], app_versions['JANS_BUILD'])), os.path.join(jans_app_dir, 'scim.war'))
    download(urljoin(maven_base_url, 'fido2-server/{0}{1}/fido2-server-{0}{1}.war'.format(app_versions['JANS_APP_VERSION'], app_versions['JANS_BUILD'])), os.path.join(jans_app_dir, 'fido2.war'))

    for unit_file in ('oxauth.service', 'scim.service', 'fido2.service'):
        unit_file_url = urljoin('https://raw.githubusercontent.com/GluuFederation/community-edition-package/master/package/systemd/', unit_file)
        download(unit_file_url, os.path.join('/etc/systemd/system', unit_file))

if os.path.exists(setup_dir):
    shutil.move(setup_dir, setup_dir+'-back.'+time.ctime())

print("Extracting jans-setup package")

setup_zip = zipfile.ZipFile(setup_zip_file, "r")
setup_par_dir = setup_zip.namelist()[0]

for filename in setup_zip.namelist():
    setup_zip.extract(filename, jans_dir)

shutil.move(os.path.join(jans_dir,setup_par_dir), setup_dir)

print("Launcing Janssen Setup")
setup_cmd = 'python3 {}/setup.py'.format(setup_dir)

if argsp.args:
    setup_cmd += ' ' + setup_cmd

os.system(setup_cmd)
