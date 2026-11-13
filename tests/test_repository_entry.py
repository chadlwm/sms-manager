"""Source-contract regression checks; not a replacement for Android browser/TalkBack tests."""
from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SOURCE=(ROOT/'app/src/main/java/com/chad/smsmanager/MainActivity.java').read_text()
ENTRY=SOURCE.split('    void showSettingsMenu(',1)[1].split('    void showSafety(){',1)[0]

class RepositoryEntryTests(unittest.TestCase):
    def test_fixed_https_repository_without_private_parameters(self):
        url=re.search(r'REPOSITORY_URL="([^"]+)"',SOURCE).group(1)
        self.assertEqual(url,'https://github.com/chadlwm/sms-manager')
        parsed=urlsplit(url)
        self.assertEqual(parsed.scheme,'https')
        self.assertEqual(parsed.query,'')
        self.assertEqual(parsed.fragment,'')
        self.assertIsNone(parsed.username)
    def test_uses_existing_settings_slot_and_accessibility_label(self):
        self.assertIn('button("安全与设置",BLUE,false,this::showSettingsMenu)',SOURCE)
        self.assertIn('settings.setContentDescription(',SOURCE)
        self.assertIn('settings.setMinHeight(dp(48))',SOURCE)
        self.assertIn('"关于 · GitHub 仓库"',ENTRY)
    def test_browser_selector_not_webview_or_sms_payload(self):
        self.assertIn('new Intent(Intent.ACTION_VIEW,Uri.parse(REPOSITORY_URL))',ENTRY)
        self.assertIn('Intent.CATEGORY_APP_BROWSER',ENTRY)
        self.assertIn('Intent.CATEGORY_BROWSABLE',ENTRY)
        self.assertNotIn('WebView',ENTRY.replace('// Fixed HTTPS destination only. Browser selector avoids routing this link through our app or a WebView.',''))
        self.assertNotIn('.putExtra(',ENTRY)
    def test_failure_and_copy_fallback(self):
        self.assertIn('catch(ActivityNotFoundException|SecurityException',ENTRY)
        self.assertIn('"无法打开浏览器"',ENTRY)
        self.assertIn('copyRepositoryLink()',ENTRY)
    def test_entry_never_deletes_or_clears_selection(self):
        self.assertNotIn('CleanupTask.start',ENTRY)
        self.assertNotIn('selected.clear',ENTRY)
        self.assertIn('if(CleanupTask.isRunning())',ENTRY)
        self.assertIn('SafetyState.needsRecovery(this)',ENTRY)
    def test_no_added_network_permission(self):
        root=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        permissions={p.get('{http://schemas.android.com/apk/res/android}name') for p in root.findall('uses-permission')}
        self.assertNotIn('android.permission.INTERNET',permissions)

if __name__=='__main__':unittest.main()
