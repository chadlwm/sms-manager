import importlib.util
import os
from pathlib import Path
import subprocess
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('release_validation',ROOT/'scripts/verify-release.py')
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
CERT='ab'*32
REPORT='Verifies\nSigner #1 certificate SHA-256 digest: '+CERT+'\n'
BADGING="package: name='com.chad.smsmanager' versionCode='4' versionName='0.2.2' platformBuildVersionName='15'\n"

class ReleaseValidationTests(unittest.TestCase):
    def test_valid_certificate_package_and_tag(self):
        data=v.validate(REPORT,BADGING,CERT,'v0.2.2')
        self.assertEqual(data['versionCode'],4)
        self.assertEqual(data['signingCertificateSha256'],CERT)
    def test_normalizes_fingerprint(self):
        self.assertEqual(v.normalize_fingerprint(':'.join(['AB']*32)),CERT)
    def test_wrong_certificate_fails(self):
        with self.assertRaises(ValueError):v.validate(REPORT,BADGING,'cd'*32)
    def test_unsigned_or_unparsed_report_fails(self):
        with self.assertRaises(ValueError):v.validate('',BADGING,CERT)
    def test_multiple_signers_fail(self):
        with self.assertRaises(ValueError):v.validate(REPORT+REPORT.replace('#1','#2'),BADGING,CERT)
    def test_wrong_tag_fails(self):
        with self.assertRaises(ValueError):v.validate(REPORT,BADGING,CERT,'v0.2.1')
    def test_wrong_package_fails(self):
        with self.assertRaises(ValueError):v.validate(REPORT,BADGING.replace('com.chad.smsmanager','other.package'),CERT)
    def test_old_version_code_fails(self):
        with self.assertRaises(ValueError):v.validate(REPORT,BADGING.replace("versionCode='4'","versionCode='3'"),CERT)
    def test_filename_injection_fails(self):
        with self.assertRaises(ValueError):v.validate(REPORT,BADGING.replace("versionName='0.2.2'","versionName='../oops'"),CERT)
    def test_short_fingerprint_fails(self):
        with self.assertRaises(ValueError):v.normalize_fingerprint('abcdef')
    def test_missing_config_fails_without_secret_values(self):
        env={k:value for k,value in os.environ.items() if not k.startswith('ANDROID_')}
        result=subprocess.run(['bash',str(ROOT/'scripts/check-signing-config.sh')],env=env,text=True,capture_output=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('ANDROID_KEYSTORE_BASE64',result.stdout)

if __name__=='__main__': unittest.main()
