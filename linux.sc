# Copyright 2019,2022-2023 VMware, Inc. All rights reserved.
# VMware Confidential
""" Linux

Linux-specific packaging script for horizonrxtest.

Maintained by the Horizon Remote Experience team:
   1. View-remote-experience@vmware.com
   2. Reviewboard groups: rx-dev-guru, scons, vdm-client
"""

import os
import vmware
from os.path import join
from SCons.Script import Dir, File

log = vmware.GetLogger('packaging')
# Map from host to publish subdir name.
hosts = {}
hosts['linux64'] = 'x64'
TARGET_NAME = 'horizonrxtestClient'
UT_TARGET_NAME = 'horizonrxut'

stageEnv = vmware.pkg.stageEnv.Copy()
stageEnv.LoadTool([
    'linkcopy',
    'zip-2.32',
    'p7zip',
    'zip-builder',
    'libssl',
])

nodeNames = [
    'horizonrxtest',
    'rxci_socket_vchan',
]

utNodeNames = [
    'horizonrxut',
    'rxSampleUnitTest',
]

headerDirs = [
    'apps/horizonrxtest/componentTest/public/',
]

pythonUtilDir = [
    'apps/horizonrxtest/componentTest/deploy',
]

allNodes = []

# Stage all binaries, libs, and debug info.
stagePP = vmware.PathPrefixer(vmware.pkg.stagePath)
stagePath = Dir(stagePP).abspath
for host in hosts:
    stageHostPath = os.path.join(stagePath, hosts[host])
    for name in nodeNames:
        allNodes += vmware.pkg.StageDeliverables(stageEnv, name, host,
                                                 stageHostPath)
    for name in utNodeNames:
        allNodes += vmware.pkg.StageDeliverables(stageEnv, name, host,
                                                 stageHostPath)


def stage_deployScritps(path):
    nodes = []
    deploySrc = '#bora/apps/horizonrxtest/componentTest/deploy'
    files = vmware.EnumerateSourceDir(deploySrc)
    nodes += vmware.DirCopy(files, Dir(deploySrc), Dir(path), stageEnv)
    return nodes


def getThirdPartyLibPaths(env):
    """ Gets a list of paths to shared libraries needed by the test app.
    """
    nodes = []
    libCurl = File(join(vmware.GetGobuildComponent('cayman_curl'),
                        'lin64+glibc217+gcc12',
                        'lib',
                        'libcurl.so.4'
                        ))
    libSsl = File(join(vmware.GetGobuildComponent('cayman_openssl'),
                       'lin64+glibc217+gcc12',
                       'usr',
                       'lib64',
                       'libssl.so.3'
                       ))
    libCrypto = File(join(vmware.GetGobuildComponent('cayman_openssl'),
                          'lin64+glibc217+gcc12',
                          'usr',
                          'lib64',
                          'libcrypto.so.3'
                          ))
    nodes += env['ZLIB_REDIST']
    libStdcpp = File(join(vmware.GetGobuildComponent(
                              vmware.pkg.stageEnv.GetGCCComponentName()),
                          'usr',
                          'x86_64-vmk-linux-gnu',
                          'lib64',
                          'libstdc++.so.6'
                          ))

    nodes += [libCurl, libSsl, libCrypto, libStdcpp]
    return nodes
for sharedLibPath in getThirdPartyLibPaths(stageEnv):
    allNodes += vmware.pkg.stageEnv.LinkCopy(join(stageHostPath,
                                                  sharedLibPath.name),
                                             sharedLibPath)


def getUtLibPaths():
    libStdcpp = File(join(vmware.GetGobuildComponent(
                              vmware.pkg.stageEnv.GetGCCComponentName()),
                          'usr',
                          'x86_64-vmk-linux-gnu',
                          'lib64',
                          'libstdc++.so.6'
                          ))
    gmock = File(join(vmware.GetGobuildComponent('cayman_googletest'),
                      'lin64+glibc217+gcc12',
                      'bin',
                      'libgmock.so'
                      ))
    gtest = File(join(vmware.GetGobuildComponent('cayman_googletest'),
                      'lin64+glibc217+gcc12',
                      'bin',
                      'libgtest.so'
                      ))
    return [libStdcpp, gmock, gtest]
for sharedUtLibPath in getUtLibPaths():
    allNodes += vmware.pkg.stageEnv.LinkCopy(join(stageHostPath,
                                                  sharedUtLibPath.name),
                                             sharedUtLibPath)


# Pubilsh all binaries, libs, and debug info.
def publishHorizonrxtestClient():
    """publishHorizonrxtestClient
    Pubish the binaries for horizonrxtest client.
    """
    nodes = []
    for host in hosts:
        zipSources = []

        # list test app and compiled librarys
        for appName in nodeNames:
            binaryNodes = vmware.pkg.LookupDeliverableNodes(appName, host)
            for node in binaryNodes:
                zipSources.append((node.dir.abspath, node.name))

        # list third party librarys
        for libs in getThirdPartyLibPaths(stageEnv):
            zipSources.append((libs.dir.abspath, libs.name))

        for pythons in getPythonPaths():
            zipSources.append((pythons.dir.abspath, pythons.name))

        zipSources.append(('#bora/apps/horizonrxtest/componentTest/deploy/',
                           'utility'))

        zipFile = File(os.path.join(publishDir, host, '%s.zip' % TARGET_NAME))
        zipNode = stageEnv.Zip(zipFile, zipSources)
        stageEnv.Depends(zipNode, binaryNodes)
        nodes += zipNode
    return nodes


def publishhorizonrxut():
    """publishHorizonrxut
    Pubish the binaries for horizonrxut.
    """
    nodes = []
    for host in hosts:
        zipSources = []

        # list test app and compiled librarys
        for appName in utNodeNames:
            binaryNodes = vmware.pkg.LookupDeliverableNodes(appName, host)
            for node in binaryNodes:
                zipSources.append((node.dir.abspath, node.name))

        # list third party librarys for ut
        for libs in getUtLibPaths():
            zipSources.append((libs.dir.abspath, libs.name))

        deploySrc = '#bora/apps/horizonrxtest/componentTest/deploy'
        zipSources.append((deploySrc,
                           'horizonut_deploy.py'))
        zipSources.append((deploySrc,
                           'horizonut_run_cases.py'))
        zipSources.append((deploySrc,
                           'horizonut_lin.ini'))

        zipSources.append((deploySrc, 'utility'))

        configPath = '#bora/apps/horizonrxtest/unitTest/sample'
        zipSources.append((configPath, 'rxSampleUnitTest.json'))

        zipFile = File(os.path.join(publishDir,
                                    host,
                                    '%s.zip' % UT_TARGET_NAME))
        zipNode = stageEnv.Zip(zipFile, zipSources)
        stageEnv.Depends(zipNode, binaryNodes)
        nodes += zipNode
    return nodes


# Stage all python deploy files.
def getPythonPaths():
    nodes = []
    pythonPP = stageP
    for headerDir in pythonUtilDir:
        sourceDirPath = '#bora/' + headerDir
        destDirPath = Dir(pythonPP).abspath
        files = vmware.EnumerateSourceDir(sourceDirPath)
        vmware.DirCopy(files, Dir(sourceDirPath),
                       Dir(destDirPath), stageEnv)
        nodes += files
    return nodes

publishDir = vmware.ReleasePackagesDir()
if publishDir:
    allNodes += stage_deployScritps(stagePath)
    allNodes += publishHorizonrxtestClient()
    allNodes += publishhorizonrxut()

vmware.Alias('horizonrxtest', allNodes)
