"""Validate public APK metadata after apksigner verifies cryptographic integrity."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys

def normalize_fingerprint(value):
    value=re.sub(r'[\s:]', '', value).lower()
    if not re.fullmatch(r'[0-9a-f]{64}',value):
        raise ValueError('Expected certificate fingerprint must contain exactly 64 hex digits.')
    return value

def validate(verification, badging, expected, tag=''):
    certs=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]+)\s*$',verification,re.M)
    if len(certs)!=1 or normalize_fingerprint(certs[0])!=normalize_fingerprint(expected):
        raise ValueError('APK signer certificate does not match the configured single stable certificate.')
    match=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging,re.M)
    if not match:
        raise ValueError('Unable to read APK version metadata.')
    package,code,version=match.groups()
    if package!='com.chad.smsmanager' or int(code)<6:
        raise ValueError('Unexpected application ID or versionCode; refusing to publish.')
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?',version):
        raise ValueError('Unexpected versionName format.')
    if tag and tag!='v'+version:
        raise ValueError('Release tag must match v + APK versionName. Bump source versions before publishing.')
    return {'applicationId':package,'versionCode':int(code),'versionName':version,
            'signingCertificateSha256':normalize_fingerprint(expected)}

def main():
    apk,report,badging,destination=map(Path,sys.argv[1:])
    metadata=validate(report.read_text(),badging.read_text(),os.environ['ANDROID_SIGNING_CERT_SHA256'],os.environ.get('RELEASE_TAG',''))
    metadata['apkSha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
    metadata['sourceCommit']=os.environ.get('GITHUB_SHA','local')
    filename=f"sms-manager-v{metadata['versionName']}.apk"
    out=Path(destination);out.mkdir(parents=True,exist_ok=True)
    # Dedicated output must not accidentally contain an unsigned/debug artifact.
    if any(out.iterdir()):
        raise ValueError('Release output directory is not empty; refusing mixed artifacts.')
    shutil.copyfile(apk,out/filename)
    (out/'SHA256SUMS.txt').write_text(metadata['apkSha256']+'  '+filename+'\n')
    (out/'release-metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
    print('Verified signed release, pinned certificate, package/version, and APK SHA-256.')

if __name__=='__main__':
    try: main()
    except (ValueError,KeyError) as error: sys.exit(str(error))
