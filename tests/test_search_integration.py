"""Android source contracts supplement the executable Java scan/matcher tests."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
MAIN=(ROOT/'app/src/main/java/com/chad/smsmanager/MainActivity.java').read_text()
class SearchIntegrationContracts(unittest.TestCase):
    def test_provider_query_is_not_sql_keyword_matching(self):
        self.assertNotIn('COALESCE(body',MAIN)
        self.assertNotIn(' LIKE ',MAIN)
        self.assertIn('scan.accept(readSms(c))',MAIN)
    def test_new_query_cancels_old_and_rejects_stale_results(self):
        self.assertIn('querySignal.cancel()',MAIN)
        self.assertIn('volatile int generation',MAIN)
        self.assertIn('token!=generation||signal.isCanceled()',MAIN)
    def test_partial_scan_is_not_a_deletable_result(self):
        self.assertIn('final SmsScan.Result scanned=message==null?scan.finish():null',MAIN)
        self.assertIn('scanned==null?new long[0]:scanned.ids',MAIN)
        self.assertIn('selection.replace(retained)',MAIN)
    def test_whole_result_selection_and_streamed_backup(self):
        self.assertIn('final long[] snapshot=matchIds',MAIN)
        self.assertIn('final long[] ids=selection.snapshot()',MAIN)
        self.assertIn('android.util.JsonWriter',MAIN)
        self.assertIn('Arrays.binarySearch(ids,c.getLong(0))',MAIN)
    def test_only_one_facet_and_explicit_type_scope(self):
        self.assertIn('facetType=kind;facetKey=bucket.key',MAIN)
        self.assertIn('scope.getSelectedItemPosition()',MAIN)
        self.assertIn('不受已点击的统计项影响',MAIN)
    def test_unapplied_conditions_cannot_use_old_selection(self):
        for method in ('confirmDelete','selectAllResults','export'):
            section=MAIN.split('void '+method+'(){',1)[1]
            self.assertTrue(section.lstrip().startswith('if(inputsChanged())'))
        self.assertIn('已查询条件：正文',MAIN)
    def test_security_guards_and_existing_repository_entry_remain(self):
        self.assertIn('if(!isDefault()||!airplane())',MAIN)
        self.assertIn('CleanupTask.start(this,ids)',MAIN)
        self.assertIn('https://github.com/chadlwm/sms-manager',MAIN)
        self.assertIn('ROLE_SMS',MAIN)
if __name__=='__main__':unittest.main()
