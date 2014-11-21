#!/usr/bin/python

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
#
# You can obtain a copy of the license at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE
# or https://OpenDS.dev.java.net/OpenDS.LICENSE.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2008 Sun Microsystems, Inc.
#      Portions Copyright 2011-2013 ForgeRock AS.





# Global variable containing the list of servers ("Server" class instances) deployed
_topologyServerList = []



# Define ChangelogServer class
class ChangelogServer:
  def __init__(self, port, id):
    self.port = port
    self.id = id
    self.changelogServerList = []

  def addChangelogServer(self, hostname, port):
    self.changelogServerList.append('%s:%s' % (hostname, port))

  def getPort(self):
    return self.port

  def getId(self):
    return self.id

  def getChangelogServerList(self):
    return self.changelogServerList

	  
# Define SynchronizedSuffix class:
class SynchronizedSuffix:
  def __init__(self, suffixDn, id):
    self.suffixDn = suffixDn
    self.id = id
    self.changelogServerList = []

  def addChangelogServer(self, hostname, port):
    self.changelogServerList.append('%s:%s' % (hostname, port))

  def getSuffixDn(self):
    return self.suffixDn

  def getId(self):
    return self.id

  def getChangelogServerList(self):
    return self.changelogServerList
	  
	    
# Define Server class
class Server:
  def __init__(self, hostname, dir, port, adminPort, sslPort, jmxPort, rootDn, rootPwd, baseDn, datadir):
    self.hostname = hostname
    self.dir = dir
    self.temp = '%s/temp' % dir
    if self.hostIsLocal(self.hostname):
      self.data = datadir
    else:
      self.data = '%s/testdata/data' % self.dir
    self.port = port
    self.adminPort = adminPort
    self.sslPort = sslPort
    self.jmxPort = jmxPort    
    self.rootDn = rootDn
    self.rootPwd = rootPwd
    self.baseDn = baseDn
    self.changelogServer = None
    self.synchronizedSuffixList = []

  def __repr__(self):
    return "Server: hostname=%s, directory=%s" % (self.hostname, self.dir)

  def addChangelogServer(self, changelogServer):
    self.changelogServer = changelogServer

  def addSynchronizedSuffix(self, synchronizedSuffix):
    self.synchronizedSuffixList.append(synchronizedSuffix)

  def getHostname(self):
    return self.hostname
  
  def getDir(self):
    return self.dir
  
  def getTmpDir(self):
    return self.temp

  def getDataDir(self):
    return self.data

  def getPort(self):
    return self.port

  def getAdminPort(self):
    return self.adminPort

  def getSslPort(self):
    return self.sslPort

  def getJmxPort(self):
    return self.jmxPort
    
  def getRootDn(self):
    return self.rootDn

  def getRootPwd(self):
    return self.rootPwd

  def getBaseDn(self):
    return self.baseDn

  def getChangelogServer(self):
    return self.changelogServer

  def getSynchronizedSuffixList(self):
    return self.synchronizedSuffixList
    
  def requiresSynchronization(self):
    return (self.changelogServer is not None) or (len(self.synchronizedSuffixList) > 0)

  def isOnlyLdapServer(self):
    return (self.changelogServer is None) and (len(self.synchronizedSuffixList) > 0)

  def isOnlyReplServer(self):
    return (self.changelogServer is not None) and (len(self.synchronizedSuffixList) == 0)

  def splitReplServer(self):
    new_hostname = self.hostname
    new_dir = '%s-repl-server' % self.dir
    new_port = str( int(self.port) + 1 )
    new_adminPort = str( int(self.adminPort) + 1 )
    new_sslPort = str( int(self.sslPort) + 1 )
    new_jmxPort = str( int(self.jmxPort) + 1 )
    new_rootDn = self.rootDn
    new_rootPwd = self.rootPwd
    new_baseDn = self.baseDn
    new_changelogServer = self.changelogServer
    self.changelogServer = None
    
    replServer = Server(new_hostname, new_dir, new_port, new_adminPort, new_sslPort,
                        new_jmxPort, new_rootDn, new_rootPwd, new_baseDn, self.data)
    replServer.addChangelogServer(new_changelogServer)

    return replServer

  def hostIsLocal(self,hostname):
    from socket import gethostbyname
    if gethostbyname(hostname).startswith('127.0'):
      return 1
    else:
      return 0



# Define the function that writes a ldif file with the replication configuration
# corresponding to the given server.
def write_replication_conf_ldif_file(path, server):
  
  ldifLines = []

  # write the main replication configuration entry
  ldifLines.append('')
	
  ldifLines.append('dn: cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config')
  ldifLines.append('objectClass: top')
  ldifLines.append('objectClass: ds-cfg-synchronization-provider')
  ldifLines.append('objectClass: ds-cfg-replication-synchronization-provider')  
  ldifLines.append('cn: Multimaster Synchronization')  
  ldifLines.append('ds-cfg-enabled: true')
  ldifLines.append('ds-cfg-java-class: org.opends.server.replication.plugin.MultimasterReplication')


  # if server is a changelog server, write its corresponding configuration
  changelogServer = server.getChangelogServer()
  if changelogServer is not None :
    port = changelogServer.getPort()
    id = changelogServer.getId()
    list = changelogServer.getChangelogServerList()
    
    ldifLines.append('')
    ldifLines.append('dn: cn=Replication Server,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config')
    ldifLines.append('objectClass: top')
    ldifLines.append('objectClass: ds-cfg-replication-server')
    ldifLines.append('cn: Replication Server')
    ldifLines.append('ds-cfg-replication-port: %s' % port)
    
    for chglgServer in list:
      ldifLines.append('ds-cfg-replication-server: %s' % chglgServer)
	  
    ldifLines.append('ds-cfg-replication-server-id: %s' % id)


  # write the domains replication configuration entry
  ldifLines.append('')
  ldifLines.append('dn: cn=domains,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config')
  ldifLines.append('objectClass: top')
  ldifLines.append('objectClass: ds-cfg-branch')
  ldifLines.append('cn: domains')
  
  # write the configuration for the synchronized suffixes, if any
  synchronizedSuffixList = server.getSynchronizedSuffixList()
  for i in range( len(synchronizedSuffixList) ):
    suffix = synchronizedSuffixList[i]
    dn = suffix.getSuffixDn()
    id = suffix.getId()
    list = suffix.getChangelogServerList()
    name = 'SUFFIX-%s' % i
    
    ldifLines.append('')
    ldifLines.append('dn: cn=%s,cn=domains,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config' % name)
    ldifLines.append('objectClass: top')
    ldifLines.append('objectClass: ds-cfg-replication-domain')
    ldifLines.append('cn: %s' % name)
    ldifLines.append('ds-cfg-base-dn: %s' % dn)
	
    for chglgServer in list:
      ldifLines.append('ds-cfg-replication-server: %s' % chglgServer)

    ldifLines.append('ds-cfg-server-id: %s' % id)
    ldifLines.append('ds-cfg-receive-status: true')	


  # write out the ldif file
  outfile = open(path,"w")
          
  for line in ldifLines:
    outfile.write("%s\n" % line)
          
  outfile.close()






# Define the function that writes a ldif file with the root suffix entry to add 
# for a given suffix.
def write_replication_add_root_suffix_ldif_file(path, suffix):
  
  ldifLines = []
  
  equalChar = suffix.find('=')
  commaChar = suffix.find(',')
  if commaChar == -1:
    commaChar = len(suffix)
  rdnType = suffix[:equalChar].strip()
  rdnValue = suffix[equalChar + 1 : commaChar].strip()
  
  if rdnType == 'o':
    objclass = 'organization'
  elif rdnType == 'ou':
    objclass = 'organizationalunit'
  elif rdnType == 'dc':
    objclass = 'domain'
  else:
    objclass = 'unknown'
  
  ldifLines.append('dn: %s' % suffix)
  ldifLines.append('%s: %s' % (rdnType,rdnValue))
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: %s' % objclass)
  
  
  # write out the ldif file
  outfile = open(path,"w")
  
  for line in ldifLines:
    outfile.write("%s\n" % line)
  
  outfile.close()    



# Define the function that writes a ldif file with an entry to add 
# under a given suffix.
def write_replication_add_single_ldif_file(path, suffix):
  
  ldifLines = []
  
  
  ldifLines.append('dn: uid=scarter,%s' % suffix)
  ldifLines.append('cn: Sam Carter')
  ldifLines.append('sn: Carter')
  ldifLines.append('givenname: Sam')
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: person')
  ldifLines.append('objectclass: organizationalPerson')
  ldifLines.append('objectclass: inetOrgPerson')
  ldifLines.append('ou: Accounting')
  ldifLines.append('ou: People')
  ldifLines.append('l: Sunnyvale')
  ldifLines.append('uid: scarter')
  ldifLines.append('mail: scarter@example.com')
  ldifLines.append('telephonenumber: +1 408 555 4798')
  ldifLines.append('facsimiletelephonenumber: +1 408 555 9751')
  ldifLines.append('roomnumber: 4612')
  ldifLines.append('userpassword: sprain')

  # write out the ldif file
  outfile = open(path,"w")
          
  for line in ldifLines:
    outfile.write("%s\n" % line)
          
  outfile.close()  






# Define the function that writes a ldif file with the entries to add 
# under a given suffix.
def write_replication_add_multiple_ldif_file(path, suffix):
  
  ldifLines = []

  ldifLines.append('dn: o=replication tests,%s' % suffix)
  ldifLines.append('o: replication tests')
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: organization')
  ldifLines.append('')
  ldifLines.append('dn: ou=People,o=replication tests,%s' % suffix)
  ldifLines.append('ou: People')
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: organizationalunit')
  ldifLines.append('')
  ldifLines.append('dn: ou=Groups, o=replication tests,%s' % suffix)
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: organizationalunit')
  ldifLines.append('ou: Groups')
  ldifLines.append('')
  ldifLines.append('dn: cn=Directory Administrators, ou=Groups, o=replication tests,%s' % suffix)
  ldifLines.append('cn: Directory Administrators')
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: groupofuniquenames')
  ldifLines.append('ou: Groups')
  ldifLines.append('uniquemember: uid=kvaughan, ou=People, o=replication tests,%s' % suffix)
  ldifLines.append('uniquemember: uid=rdaugherty, ou=People, o=replication tests,%s' % suffix)
  ldifLines.append('uniquemember: uid=hmiller, ou=People, o=replication tests,%s' % suffix)
  ldifLines.append('')
  ldifLines.append('dn: ou=Special Users,o=replication tests,%s' % suffix)
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: organizationalUnit')
  ldifLines.append('ou: Special Users')
  ldifLines.append('description: Special Administrative Accounts')
  ldifLines.append('')
  ldifLines.append('dn: uid=scarter,ou=People,o=replication tests,%s' % suffix)
  ldifLines.append('cn: Sam Carter')
  ldifLines.append('sn: Carter')
  ldifLines.append('givenname: Sam')
  ldifLines.append('objectclass: top')
  ldifLines.append('objectclass: person')
  ldifLines.append('objectclass: organizationalPerson')
  ldifLines.append('objectclass: inetOrgPerson')
  ldifLines.append('ou: Accounting')
  ldifLines.append('ou: People')
  ldifLines.append('l: Sunnyvale')
  ldifLines.append('uid: scarter')
  ldifLines.append('mail: scarter@example.com')
  ldifLines.append('telephonenumber: +1 408 555 4798')
  ldifLines.append('facsimiletelephonenumber: +1 408 555 9751')
  ldifLines.append('roomnumber: 4612')
  ldifLines.append('userpassword: sprain')

  # write out the ldif file
  outfile = open(path,"w")
          
  for line in ldifLines:
    outfile.write("%s\n" % line)
          
  outfile.close()  




# Define the function that writes a ldif file with the modify to operate
# on an entry in a given suffix.
def write_replication_mod_ldif_file(path, dn, mod_type, attr_type, attr_value):
  
  ldifLines = []

  ldifLines.append('dn: %s' % dn)
  ldifLines.append('changetype: modify')
  ldifLines.append('%s: %s' % (mod_type,attr_type))
  if attr_value != None :  
    ldifLines.append('%s: %s' % (attr_type,attr_value))
  
  
  # write out the ldif file
  outfile = open(path,"w")
          
  for line in ldifLines:
    outfile.write("%s\n" % line)
          
  outfile.close()  
  


# Define the function that writes a ldif file with the modify to operate
# on an entry in a given suffix.
def write_replication_mod_binary_ldif_file(path, dn, mod_type, attr_type, binary_value_path):
  
  # open file and read the binary value (which is encoded in base64)
  binaryValueFile = open(binary_value_path, "r")
  binaryValue = binaryValueFile.read()
  binaryValueFile.close()

  ldifLines = []

  ldifLines.append('dn: %s' % dn)
  ldifLines.append('changetype: modify')
  ldifLines.append('%s: %s' % (mod_type,attr_type))
  ldifLines.append('%s:: %s' % (attr_type,binaryValue))
  
  
  # write out the ldif file
  outfile = open(path,"w")
          
  for line in ldifLines:
    outfile.write("%s\n" % line)
          
  outfile.close()  
  
  


# Define the function that writes a ldif file with the modDN to operate
# on an entry in a given suffix.
def write_replication_moddn_ldif_file(path, dn, newrdn, newsuperior, deleteoldrdn):
  
  ldifLines = []

  ldifLines.append('dn: %s' % dn)
  ldifLines.append('changetype: moddn')
  ldifLines.append('newRDN: %s' % newrdn)
  ldifLines.append('deleteOldRDN: %s' % deleteoldrdn)  
  if newsuperior != None:
    ldifLines.append('newSuperior: %s' % newsuperior)  

  
  
  # write out the ldif file
  outfile = open(path,"w")
          
  for line in ldifLines:
    outfile.write("%s\n" % line)
          
  outfile.close()  

