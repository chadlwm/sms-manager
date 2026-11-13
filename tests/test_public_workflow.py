"""Regression guards for the public-workflow privilege boundary (not a YAML security proof)."""
from pathlib import Path
import unittest
import re

ROOT=Path(__file__).resolve().parents[1]

class PublicWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.workflow=(ROOT/'.github/workflows/android.yml').read_text()
    def test_no_privileged_pull_request_trigger(self):
        self.assertNotIn('pull_request_target:',self.workflow)
        self.assertNotIn('workflow_run:',self.workflow)
    def test_test_job_has_no_signing_secrets_or_write_token(self):
        job=self.workflow.split('  test-build:',1)[1].split('  signed-release:',1)[0]
        self.assertNotIn('secrets.',job)
        self.assertNotIn('contents: write',job)
        self.assertIn('persist-credentials: false',job)
        self.assertIn('cache-read-only: true',job)
    def test_signing_restricted_to_original_repo_and_main_dispatch(self):
        job=self.workflow.split('  signed-release:',1)[1].split('  publish:',1)[0]
        self.assertIn("github.repository == 'chadlwm/sms-manager'",job)
        self.assertIn("github.ref == 'refs/heads/main'",job)
        self.assertIn('cache-disabled: true',job)
        self.assertLess(job.index('git merge-base --is-ancestor HEAD refs/remotes/origin/main'),job.index('secrets.ANDROID_KEYSTORE_BASE64'))
    def test_publish_depends_on_verified_signing(self):
        job=self.workflow.split('  publish:',1)[1]
        self.assertIn('needs: signed-release',job)
        self.assertIn('name: signed-installable-release',job)
        self.assertNotIn('debug-test-only',job)
    def test_third_party_actions_are_commit_pinned(self):
        uses=re.findall(r'uses: ([^\s#]+)',self.workflow)
        self.assertGreater(len(uses),0)
        for action in uses:self.assertRegex(action,r'@[0-9a-f]{40}$')
    def test_public_restart_keeps_monotonic_android_version(self):
        self.assertIn("versionCode 6; versionName '0.1.1'",(ROOT/'app/build.gradle').read_text())
        self.assertIn('## [0.1.1]',(ROOT/'CHANGELOG.md').read_text())

if __name__=='__main__':unittest.main()
