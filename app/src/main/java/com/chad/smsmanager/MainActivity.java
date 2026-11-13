package com.chad.smsmanager;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Local-only SMS cleanup UI. No role grant or query ever starts deletion automatically. */
public class MainActivity extends Activity {
    private static final String REPOSITORY_URL="https://github.com/chadlwm/sms-manager";
    static final int INK=0xFF172B40, MUTED=0xFF60758B, BLUE=0xFF246BFE, BG=0xFFF3F6FA, RED=0xFFB73343;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler ui=new Handler(Looper.getMainLooper());
    final ArrayList<SmsRecord> rows=new ArrayList<>();
    SmsSelection selection=new SmsSelection();
    long[] matchIds=new long[0], exportIds;
    static final int PAGE_SIZE=SmsScan.PAGE_SIZE;
    int pageIndex=0, facetType=0; String facetKey=null;
    SmsSearch.Result statistics;
    String appliedKeyword="",appliedSender="",scopeDescription="",appliedPrefixes=""; boolean appliedExact=true; int appliedScope=0;
    String[] scopeNames={"全部 SMS（含已发送/草稿）","仅收件箱（收到的 SMS）","仅已发送 SMS"};
    LinearLayout filtersPanel;Button filterToggle;
    Spinner scope; TextView facetLabel,pageLabel,dataScope; Button statsButton,previousPage,nextPage;
    CancellationSignal querySignal; static volatile boolean exporting; boolean observedExport;
    EditText keyword,sender; CheckBox exact;
    TextView status,recovery,operation,empty,selectedLabel;
    Button delete,selectAll,backup,cancel,access; ProgressBar progress; BaseAdapter adapter;
    boolean busy,observedRunning,permissionRequested,refreshPending,resumed; int roleAttempts;
    final Runnable roleCheck=()->checkRoleResult(); volatile int generation;
    final Runnable monitor=new Runnable(){public void run(){
        boolean running=CleanupTask.isRunning(); refreshSafety();
        if(observedRunning&&!running)load(false);
        observedRunning=running; if(observedExport&&!exporting)load(false);observedExport=exporting;showCompletion();showExportCompletion();ui.postDelayed(this,400);
    }};
    static SmsRecord readSms(Cursor c){return new SmsRecord(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4));}
    static final class RetainedSelection { final long[] selected,export;RetainedSelection(long[] s,long[] e){selected=s;export=e;} }
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(16),dp(14),dp(16),dp(14));v.setBackground(shape(Color.WHITE,20));return v;}
    void spaced(LinearLayout root,View v,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(margin);root.addView(v,p);}
    Button button(String label,int color,boolean primary,View.OnClickListener click){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:color);b.setBackground(shape(primary?color:0xFFEAF0F8,12));b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(10),dp(4),dp(10),dp(4));b.setOnClickListener(click);return b;}
    void weighted(LinearLayout row,View view){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),0,dp(3),0);row.addView(view,p);}
    EditText input(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setPadding(dp(14),dp(10),dp(14),dp(10));e.setBackground(shape(BG,12));e.setMinHeight(dp(46));return e;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(12),dp(18),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18),i.top+dp(12),dp(18),i.bottom+dp(10));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headings=column();headings.addView(text("短信清理",28,INK,true));headings.addView(text("留住重要消息，让收件箱轻一点",12,MUTED,false));header.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("安全与设置",BLUE,false,this::showSettingsMenu);settings.setMinHeight(dp(48));settings.setMinimumHeight(dp(48));settings.setContentDescription("安全与设置菜单，含默认短信应用和关于项目");settings.setTooltipText("安全设置与关于项目");header.addView(settings);spaced(root,header,12);
        filterToggle=button("收起筛选",BLUE,false,v->setFiltersExpanded(filtersPanel.getVisibility()!=View.VISIBLE));spaced(root,filterToggle,5);
        LinearLayout filters=card();filtersPanel=filters;
        keyword=input("搜索正文，例如：优惠、验证码");spaced(filters,keyword,8);
        sender=input("发件号 / 收件地址，例如：106…");spaced(filters,sender,5);
        scope=new Spinner(this);ArrayAdapter<String> scopes=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,scopeNames);scope.setAdapter(scopes);scope.setContentDescription("短信数据范围");filters.addView(scope,new LinearLayout.LayoutParams(-1,dp(36)));
        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        exact=new CheckBox(this);exact.setText("精确发件人");exact.setTextSize(12);exact.setTextColor(MUTED);exact.setChecked(true);searchRow.addView(exact,new LinearLayout.LayoutParams(0,dp(44),1));
        searchRow.addView(button("重置",MUTED,false,v->{if(!locked()){keyword.setText("");sender.setText("");exact.setChecked(true);scope.setSelection(0);facetType=0;facetKey=null;load(true);}}));
        Button search=button("查询",BLUE,true,v->{hideKeyboard();load(true);});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(76),dp(44));sp.leftMargin=dp(8);searchRow.addView(search,sp);filters.addView(searchRow);spaced(root,filters,8);
        facetLabel=text("",12,BLUE,true);facetLabel.setPadding(dp(10),dp(6),dp(10),dp(6));facetLabel.setBackground(shape(0xFFE1ECFF,10));facetLabel.setOnClickListener(v->{facetType=0;facetKey=null;load(true);});facetLabel.setContentDescription("当前统计筛选，点击清除");spaced(root,facetLabel,5);
        dataScope=text("正文搜索忽略空白、全半角、大小写和零宽格式符",11,MUTED,false);dataScope.setMaxLines(3);spaced(root,dataScope,4);
        keyword.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);sender.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        TextView.OnEditorActionListener submit=(v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){hideKeyboard();load(true);return true;}return false;};keyword.setOnEditorActionListener(submit);sender.setOnEditorActionListener(submit);
        recovery=text("",12,MUTED,false);recovery.setPadding(dp(12),dp(10),dp(12),dp(10));recovery.setBackground(shape(0xFFE7EEF9,12));recovery.setOnClickListener(v->showSafety());spaced(root,recovery,8);
        operation=text("",13,INK,false);operation.setPadding(dp(12),dp(10),dp(12),dp(10));operation.setMaxLines(3);operation.setEllipsize(android.text.TextUtils.TruncateAt.END);operation.setBackground(shape(0xFFE7F0FE,12));operation.setOnClickListener(v->showResult());spaced(root,operation,8);
        LinearLayout info=new LinearLayout(this);info.setGravity(Gravity.CENTER_VERTICAL);status=text("准备读取…",13,MUTED,false);info.addView(status,new LinearLayout.LayoutParams(0,dp(38),1));statsButton=button("统计",BLUE,false,v->showStatsMenu());info.addView(statsButton);selectAll=button("全选结果",BLUE,false,v->selectAllResults());LinearLayout.LayoutParams allParams=new LinearLayout.LayoutParams(-2,-2);allParams.leftMargin=dp(5);info.addView(selectAll,allParams);spaced(root,info,6);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        FrameLayout listArea=new FrameLayout(this);root.addView(listArea,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(8));list.setClipToPadding(false);list.setPadding(0,dp(6),0,dp(8));listArea.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout placeholder=column();placeholder.setGravity(Gravity.CENTER);placeholder.setPadding(dp(20),dp(16),dp(20),dp(16));empty=text("还没有短信结果",17,MUTED,true);empty.setGravity(Gravity.CENTER);placeholder.addView(empty);access=button("允许读取短信",BLUE,true,v->requestRead());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(48));ap.topMargin=dp(16);placeholder.addView(access,ap);listArea.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(placeholder);
        adapter=new BaseAdapter(){public int getCount(){return rows.size();}public Object getItem(int i){return rows.get(i);}public long getItemId(int i){return rows.get(i).id;}
            public View getView(int i,View recycled,ViewGroup parent){
                SmsRecord s=rows.get(i);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.TOP);row.setPadding(dp(10),dp(12),dp(12),dp(12));row.setBackground(shape(selection.contains(s.id)?0xFFE8F0FF:Color.WHITE,16));
                CheckBox check=new CheckBox(MainActivity.this);check.setContentDescription("选择来自 "+s.address+" 的短信");check.setChecked(selection.contains(s.id));check.setEnabled(!locked());row.addView(check,new LinearLayout.LayoutParams(dp(44),dp(44)));
                LinearLayout content=column();row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
                TextView address=text(s.address==null?"未知发件人":s.address,16,INK,true);content.addView(address);
                TextView date=text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(s.date))+(s.type==2?" · 已发送":""),11,MUTED,false);content.addView(date);
                TextView body=text(s.body==null?"（空短信）":s.body,14,INK,false);body.setMaxLines(3);body.setEllipsize(android.text.TextUtils.TruncateAt.END);body.setPadding(0,dp(7),0,dp(6));content.addView(body);
                TextView detail=text("查看全文  ›",12,BLUE,true);detail.setPadding(0,dp(8),0,dp(8));content.addView(detail);detail.setOnClickListener(v->showSms(s));
                check.setOnCheckedChangeListener((v,on)->{selection.toggle(s.id,on);row.setBackground(shape(on?0xFFE8F0FF:Color.WHITE,16));updateSelection();});
                content.setOnClickListener(v->{if(!locked())check.setChecked(!check.isChecked());});row.setOnLongClickListener(v->{showSms(s);return true;});return row;
            }};list.setAdapter(adapter);
        LinearLayout pager=new LinearLayout(this);pager.setGravity(Gravity.CENTER_VERTICAL);
        previousPage=button("上一页",BLUE,false,v->{if(!locked()&&pageIndex>0){pageIndex--;load(false);}});pager.addView(previousPage);
        pageLabel=text("第 1 页",12,MUTED,false);pageLabel.setGravity(Gravity.CENTER);pager.addView(pageLabel,new LinearLayout.LayoutParams(0,dp(42),1));
        nextPage=button("下一页",BLUE,false,v->{if(!locked()&&(pageIndex+1)*PAGE_SIZE<matchIds.length){pageIndex++;load(false);}});pager.addView(nextPage);root.addView(pager);
        LinearLayout bottom=column();bottom.setPadding(0,dp(8),0,0);selectedLabel=text("尚未选择短信",13,INK,true);selectedLabel.setPadding(dp(3),0,0,dp(8));bottom.addView(selectedLabel);
        LinearLayout tools=new LinearLayout(this);backup=button("备份所选",BLUE,false,v->export());delete=button("删除所选",RED,true,v->confirmDelete());weighted(tools,backup);weighted(tools,delete);bottom.addView(tools);
        cancel=button("停止后续删除",RED,false,v->{CleanupTask.cancel();Toast.makeText(this,"已请求停止。已经删除的短信无法撤销。",Toast.LENGTH_LONG).show();});cancel.setVisibility(View.GONE);spaced(bottom,cancel,0);root.addView(bottom);
        if(state!=null){keyword.setText(state.getString("keyword",""));sender.setText(state.getString("sender",""));exact.setChecked(state.getBoolean("exact",true));scope.setSelection(state.getInt("scope",0));facetType=state.getInt("facetType",0);facetKey=state.getString("facetKey");pageIndex=state.getInt("pageIndex",0);long[] ids=state.getLongArray("selected");if(ids!=null)selection.replace(ids);exportIds=state.getLongArray("exportIds");permissionRequested=state.getBoolean("permissionRequested",false);}
        Object retained=getLastNonConfigurationInstance();if(retained instanceof RetainedSelection){RetainedSelection saved=(RetainedSelection)retained;selection.replace(saved.selected);exportIds=saved.export;}else if(state!=null&&state.getBoolean("large_selection",false))Toast.makeText(this,"进程重建后大批量选择已清空，请重新核对并选择",Toast.LENGTH_LONG).show();
        if(state!=null)setFiltersExpanded(state.getBoolean("filtersExpanded",true));
        setContentView(root);
        if(Intent.ACTION_SENDTO.equals(getIntent().getAction()))error("这是清理工具，不支持发送短信。请在安全与设置中恢复原短信应用后发送。");
    }
    boolean locked(){return busy||exporting||CleanupTask.isRunning();}
    boolean isDefault(){return SafetyState.isDefault(this);}
    void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keyword.getWindowToken(),0);keyword.clearFocus();sender.clearFocus();}
    void showSms(SmsRecord s){new AlertDialog.Builder(this).setTitle(s.address==null?"短信详情":s.address).setMessage(s.body).setPositiveButton("关闭",null).setNeutralButton("查此发件人",(d,w)->{if(locked())return;sender.setText(s.address);keyword.setText("");exact.setChecked(true);facetType=0;facetKey=null;load(true);}).show();}
    void showSettingsMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"安全与默认短信应用");
        menu.getMenu().add(0,2,1,"关于 · GitHub 仓库");
        menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)showSafety();else if(item.getItemId()==2)showAbout();return true;});
        menu.show();
    }
    void showAbout(){
        String version="";
        try {version=" v"+getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException ignored){}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("关于短信清理"+version)
            .setMessage("查看源代码、更新日志与使用说明：\n\n"+REPOSITORY_URL+"\n\n仅在你点击后使用外部浏览器打开，不发送短信内容、筛选条件或操作记录。网页由 GitHub 提供；请勿在公开 Issue 中上传私人短信或备份。")
            .setPositiveButton("浏览器打开",(d,w)->openRepository())
            .setNeutralButton("复制链接",(d,w)->copyRepositoryLink())
            .setNegativeButton("关闭",null).create();
        dialog.show();TextView message=dialog.findViewById(android.R.id.message);if(message!=null)message.setTextIsSelectable(true);
    }
    void copyRepositoryLink(){
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("短信清理 GitHub 仓库",REPOSITORY_URL));
        Toast.makeText(this,"已复制 GitHub 仓库链接",Toast.LENGTH_SHORT).show();
    }
    void openRepository(){
        if(CleanupTask.isRunning()){error("清理正在进行中，请等待完成或停止后续删除，再打开仓库。已删除的短信不能撤销。");return;}
        if(SafetyState.needsRecovery(this)){
            new AlertDialog.Builder(this).setTitle("先留意默认短信应用")
                .setMessage("打开浏览器不会恢复原默认短信应用。当前收信仍可能受到影响，请尽快恢复原应用。")
                .setPositiveButton("继续打开",(d,w)->launchRepositoryBrowser())
                .setNeutralButton("恢复设置",(d,w)->openSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                .setNegativeButton("取消",null).show();return;
        }
        launchRepositoryBrowser();
    }
    void launchRepositoryBrowser(){
        // Fixed HTTPS destination only. Browser selector avoids routing this link through our app or a WebView.
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(REPOSITORY_URL));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        Intent browser=new Intent(Intent.ACTION_MAIN);browser.addCategory(Intent.CATEGORY_APP_BROWSER);intent.setSelector(browser);
        try {startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){
            new AlertDialog.Builder(this).setTitle("无法打开浏览器")
                .setMessage("未找到可用浏览器，或系统阻止了打开。可以复制仓库链接，在你选择的浏览器中粘贴访问。短信和选择状态未被修改。")
                .setPositiveButton("复制链接",(d,w)->copyRepositoryLink()).setNegativeButton("关闭",null).show();
        }
    }
    void showSafety(){String event=SafetyState.prefs(this).getString("last_event","");new AlertDialog.Builder(this).setTitle("安全与默认短信应用").setMessage(SafetyState.guidance(this)+"\n\n状态诊断：\n"+SafetyState.diagnostics(this)+"\n\n建议先备份，开启飞行模式后临时授权清理。清理完立即恢复原短信应用，再关闭飞行模式。飞行模式也不能保证收信不受影响。\n\n本工具不支持 MMS、RCS、发送和快捷回复。"+(event.isEmpty()?"":"\n\n接收异常："+event)).setPositiveButton("打开默认应用设置",(d,w)->openSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)).setNeutralButton("本应用权限",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("关闭",null).show();}
    void openSettings(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException e){error("未找到对应设置页面，请手动进入系统设置 → 应用 → 默认应用/权限。");}}
    @Override public void onResume(){super.onResume();resumed=true;if(keyword!=null){SafetyState.refreshNotification(this);refreshSafety();load(false);ui.removeCallbacks(monitor);ui.post(monitor);
        if(SafetyState.prefs(this).getBoolean("role_request_pending",false)){roleAttempts=0;ui.removeCallbacks(roleCheck);ui.post(roleCheck);}
    }}
    @Override public void onPause(){super.onPause();resumed=false;ui.removeCallbacks(monitor);ui.removeCallbacks(roleCheck);}
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("keyword",keyword.getText().toString());b.putString("sender",sender.getText().toString());b.putBoolean("exact",exact.isChecked());b.putBoolean("permissionRequested",permissionRequested);b.putBoolean("filtersExpanded",filtersPanel.getVisibility()==View.VISIBLE);b.putInt("scope",scope.getSelectedItemPosition());b.putInt("facetType",facetType);b.putString("facetKey",facetKey);b.putInt("pageIndex",pageIndex);if(selection.size()<=2000)b.putLongArray("selected",selection.snapshot());else b.putBoolean("large_selection",true);if(exportIds!=null&&exportIds.length<=2000)b.putLongArray("exportIds",exportIds);}
    @Override public Object onRetainNonConfigurationInstance(){return new RetainedSelection(selection.snapshot(),exportIds);}
    void refreshSafety(){
        boolean running=CleanupTask.isRunning();recovery.setText(SafetyState.prefs(this).getBoolean("role_request_pending",false)?"正在核对系统短信角色 · 尚未执行删除…":isDefault()?"⚠ 当前仍为默认短信应用 · 清理后请恢复  ›":SafetyState.roleStatus(this)==DefaultSmsPolicy.Status.INCONSISTENT?"⚠ 系统角色状态不一致 · 点击核对，暂不删除  ›":"本机离线处理 · 删除需临时授权 · 安全说明  ›");recovery.setTextColor(isDefault()?0xFF8B4D0E:MUTED);recovery.setBackground(shape(isDefault()?0xFFFFEBCD:0xFFE7EEF9,12));
        String result=CleanupTask.summary(this);operation.setText(result.isEmpty()?"":result+"  点击查看详情 ›");String outcome=SafetyState.prefs(this).getString("delete_outcome","");boolean failed=outcome.equals("failed")||outcome.equals("partial");operation.setBackground(shape(failed?0xFFFFE8E9:0xFFE7F0FE,12));operation.setTextColor(failed?RED:INK);operation.setVisibility(result.isEmpty()?View.GONE:View.VISIBLE);cancel.setVisibility(running?View.VISIBLE:View.GONE);progress.setVisibility(locked()?View.VISIBLE:View.GONE);updateSelection();
    }
    void showResult(){String result=CleanupTask.summary(this);if(result.isEmpty())return;new AlertDialog.Builder(this).setTitle(CleanupTask.isRunning()?"清理进行中":"清理结果").setMessage(result+"\n\n若未成功，请检查 ColorOS 中本应用短信权限及默认短信应用设置。不能确认厂商拦截原因，不会自动扩大删除范围。\n清理后请恢复原短信应用。").setPositiveButton("知道了",null).setNeutralButton("权限与恢复",(d,w)->showSafety()).show();}
    void showCompletion(){android.content.SharedPreferences p=SafetyState.prefs(this);long id=p.getLong("delete_operation_id",0);if(id==0||CleanupTask.isRunning()||id==p.getLong("delete_seen_id",0))return;p.edit().putLong("delete_seen_id",id).apply();showResult();}
    void showExportCompletion(){android.content.SharedPreferences p=SafetyState.prefs(this);long id=p.getLong("export_id",0);if(id==0||exporting||id==p.getLong("export_seen",0))return;p.edit().putLong("export_seen",id).apply();error(p.getBoolean("export_running",false)?"备份进程曾中断，目标文件可能不完整，不能据此删除重要短信。请重新导出。":p.getString("export_result","备份结果无法确认，请重新核对。"));}
    void updateSelection(){boolean changed=inputsChanged();boolean lock=locked()||changed||SafetyState.prefs(this).getBoolean("role_request_pending",false);selectedLabel.setText(CleanupTask.isRunning()?CleanupTask.progress(this):changed?"筛选条件已变，请先点击查询":selection.isEmpty()?"点卡片选择 · 全选结果覆盖所有页":"已选 "+selection.size()+" 条（可跨页）");delete.setText("删除所选"+(selection.isEmpty()?"":"（"+selection.size()+"）"));delete.setEnabled(!lock&&!selection.isEmpty());delete.setAlpha(delete.isEnabled()?1f:.45f);backup.setEnabled(!lock&&!selection.isEmpty());backup.setAlpha(backup.isEnabled()?1f:.45f);selectAll.setEnabled(!lock&&matchIds.length>0);selectAll.setText(matchIds.length>0&&selection.size()==matchIds.length?"取消全选":"全选结果");statsButton.setEnabled(!locked()&&statistics!=null);previousPage.setEnabled(!locked()&&pageIndex>0);nextPage.setEnabled(!locked()&&(pageIndex+1)*PAGE_SIZE<matchIds.length);}
    void update(){status.setText(busy?"正在扫描…":matchIds.length+" 条结果");adapter.notifyDataSetChanged();facetLabel.setVisibility(facetType==0?View.GONE:View.VISIBLE);facetLabel.setText("统计筛选："+facetDescription()+"  × 清除");pageLabel.setText("第 "+(pageIndex+1)+" / "+Math.max(1,(matchIds.length+PAGE_SIZE-1)/PAGE_SIZE)+" 页 · 每页100条");refreshSafety();}
    void error(String value){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("操作提示").setMessage(value).setPositiveButton("知道了",null).show();}
    void requestRead(){if(checkSelfPermission(Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED){load(false);return;}if(permissionRequested&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)){new AlertDialog.Builder(this).setTitle("开启短信读取权限").setMessage("请到本应用权限页面允许读取短信，然后返回重试。部分 ColorOS 版本对侧载应用另有限制。").setPositiveButton("打开权限设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("取消",null).show();return;}permissionRequested=true;requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.READ_SMS,Manifest.permission.POST_NOTIFICATIONS}:new String[]{Manifest.permission.READ_SMS},10);}
    String shortFilter(String value){String clean=SmsSearch.normalize(value);int points=clean.codePointCount(0,clean.length());return points<=10?clean:clean.substring(0,clean.offsetByCodePoints(0,10))+"…";}
    void setFiltersExpanded(boolean expanded){filtersPanel.setVisibility(expanded?View.VISIBLE:View.GONE);String k=shortFilter(keyword.getText().toString()),a=shortFilter(sender.getText().toString());filterToggle.setText(expanded?"收起筛选":"修改筛选"+(inputsChanged()?"（待查询）":"")+" · "+(k.isEmpty()?"正文不限":k)+" · "+(a.isEmpty()?"号码不限":a+(exact.isChecked()?" 精确":" 包含")));}
    void load(boolean reset){
        if(CleanupTask.isRunning()||exporting)return;
        if(reset){selection.clear();pageIndex=0;setFiltersExpanded(false);}
        if(querySignal!=null)querySignal.cancel();final int token=++generation;final CancellationSignal signal=new CancellationSignal();querySignal=signal;
        if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED){busy=false;rows.clear();matchIds=new long[0];statistics=null;selection.clear();empty.setText("允许读取短信，开始整理\n内容仅在手机内处理");access.setVisibility(View.VISIBLE);update();return;}
        final String k=keyword.getText().toString(),a=sender.getText().toString(),rawPrefixes=SafetyState.prefs(this).getString("custom_prefixes","106\n955");final boolean useExact=exact.isChecked();final int scopeMode=scope.getSelectedItemPosition(),wantedPage=pageIndex;
        final SmsSearch.Query baseQuery=new SmsSearch.Query(k,a,useExact,null,null),activeQuery=facetQuery();final long[] oldSelection=selection.snapshot();
        final List<String> prefixes;try{prefixes=SmsSearch.parsePrefixes(rawPrefixes);}catch(IllegalArgumentException e){busy=false;rows.clear();matchIds=new long[0];statistics=null;selection.clear();update();error(e.getMessage());return;}
        access.setVisibility(View.GONE);empty.setText("正在扫描全部可读取 SMS…");busy=true;update();
        worker.execute(()->{SmsScan scan=new SmsScan(baseQuery,activeQuery,prefixes,scopeMode,wantedPage);String failure=null;long lastProgress=0;
            try(Cursor c=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type"},null,null,"date DESC, _id DESC",signal)){
                if(c==null)throw new IllegalStateException("No cursor");
                while(c.moveToNext()){
                    if(token!=generation||signal.isCanceled())return;scan.accept(readSms(c));
                    long now=SystemClock.elapsedRealtime();if(now-lastProgress>350){lastProgress=now;final long progressCount=scan.read;ui.post(()->{if(token==generation&&!isDestroyed())status.setText("扫描 "+progressCount+" 条…");});}
                }
            }catch(Exception e){if(token!=generation||signal.isCanceled())return;failure="读取未完整完成（"+e.getClass().getSimpleName()+"）。本次不展示部分统计，也不允许依据部分结果删除。请检查短信权限后重试。";}
            if(token!=generation||signal.isCanceled())return;
            final String message=failure;final SmsScan.Result scanned=message==null?scan.finish():null;final long totalRead=scan.read;final SmsSearch.Result result=scanned==null?null:scanned.statistics;final long[] ids=scanned==null?new long[0]:scanned.ids;final long[] retained=SmsSelection.intersect(oldSelection,ids);
            ui.post(()->{if(isDestroyed()||token!=generation)return;busy=false;rows.clear();if(scanned!=null)rows.addAll(scanned.visible);matchIds=ids;statistics=result;selection.replace(retained);appliedKeyword=k;appliedSender=a;appliedExact=useExact;appliedScope=scopeMode;appliedPrefixes=rawPrefixes;scopeDescription=scopeNames[scopeMode];setFiltersExpanded(filtersPanel.getVisibility()==View.VISIBLE);
                dataScope.setText(message==null?"范围："+scopeDescription+" · 已完整读取 "+totalRead+" 条\n基础筛选 "+result.matched+" 条；统计不含当前统计项。数量以本次扫描为准。":"扫描失败，结果与统计已清空，未执行删除。");
                empty.setText(message==null?"没有匹配的短信\n可重置基础条件或清除统计筛选":message);update();
                if(message!=null){error(message);return;}if(pageIndex>0&&pageIndex*PAGE_SIZE>=matchIds.length){pageIndex=Math.max(0,(matchIds.length-1)/PAGE_SIZE);load(false);}
            });
        });
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10){load(false);if(g.length==0||g[0]!=PackageManager.PERMISSION_GRANTED)error("未获得读取权限，因此尚未读取或删除短信。可点击“允许读取短信”重试。");}}
    SmsSearch.Query facetQuery(){return new SmsSearch.Query("",facetType==1?facetKey:"",true,facetType==2?facetKey:null,facetType==3?facetKey:null);}
    String facetDescription(){return facetType==1?"号码："+SmsSearch.senderLabel(facetKey):facetType==2?"前缀："+facetKey:facetType==3?"签名："+SmsSearch.signatureLabel(facetKey):"不限统计项";}
    boolean inputsChanged(){return !appliedKeyword.equals(keyword.getText().toString())||!appliedSender.equals(sender.getText().toString())||appliedExact!=exact.isChecked()||appliedScope!=scope.getSelectedItemPosition();}
    void selectAllResults(){
        if(inputsChanged()){error("筛选条件已改变，请先点击查询更新结果，再全选。");return;}
        if(locked()||matchIds.length==0)return;
        if(selection.size()==matchIds.length){selection.clear();update();return;}
        final long[] snapshot=matchIds;final int token=generation;busy=true;update();
        worker.execute(()->{final long[] sorted=SmsSelection.sortedUnique(snapshot);ui.post(()->{if(isDestroyed()||token!=generation)return;selection.replace(sorted);busy=false;update();Toast.makeText(this,"已选择全部 "+selection.size()+" 条结果，包含其他页",Toast.LENGTH_SHORT).show();});});
    }
    void showStatsMenu(){
        if(locked()||statistics==null)return;
        if(inputsChanged()){error("基础条件已改动，请先点击查询更新统计，避免使用旧数量。");return;}
        new AlertDialog.Builder(this).setTitle("统计 · 基础筛选 "+statistics.matched+" 条")
            .setItems(new String[]{"发件号 / 收件地址","自定义号码前缀","短信签名","搜索与统计规则"},(d,which)->{if(which==3)showSearchRules();else showBuckets(which+1);})
            .setNegativeButton("关闭",null).show();
    }
    void showSearchRules(){new AlertDialog.Builder(this).setTitle("范围与匹配规则").setMessage("搜索读取完整 SMS 正文，不只搜索卡片预览。正文忽略大小写、全半角、空白（含换行）和零宽格式控制；关键词按字面包含，不是正则，% 和 _ 没有通配意义。号码按规范化后的精确/包含/前缀匹配，不自动去除国家码。\n\n统计覆盖当前数据范围及正文/号码基础条件的全部命中，不受已点击的统计项影响。每次只能选一个统计项；点击新项会替换旧项并清空选择。列表每页100条，全选结果覆盖所有页。\n\n全部 SMS 包含已发送/草稿，其号码是收件地址；只统计发件号请切换到“仅收件箱”。MMS/RCS不在范围内。\n\n签名只取正文开头或结尾紧邻的【…】或[…]，两端都有时开头优先。内容1–40个Unicode码点，不接受内部换行/控制字符或嵌套括号；中间括号不算。规范化后分组，无有效签名归“无签名”。这是启发式标签，不是品牌身份认证。\n\n自定义前缀最多30个，单项最多64码点，逗号/分号/换行分隔。重叠前缀各自计数，不能相加作为短信总数。数量以本次扫描为准，短信变化后需重新查询。")
            .setPositiveButton("知道了",null).show();}
    void showBuckets(int kind){
        if(statistics==null)return;
        final List<SmsSearch.Bucket> source=kind==1?statistics.senders:kind==2?statistics.prefixes:statistics.signatures;
        final ArrayList<SmsSearch.Bucket> displayed=new ArrayList<>(source);
        LinearLayout panel=column();panel.setPadding(dp(16),dp(8),dp(16),0);
        TextView context=text("基础匹配 "+statistics.matched+" 条 · "+scopeDescription+"\n不含当前统计项；点击后替换统计筛选"+(kind==2?"\n重叠前缀分别计数，不能相加":""),12,MUTED,false);spaced(panel,context,8);
        EditText find=input("查找统计项");spaced(panel,find,8);
        FrameLayout area=new FrameLayout(this);panel.addView(area,new LinearLayout.LayoutParams(-1,dp(280)));
        ListView list=new ListView(this);area.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView noItems=text(kind==2?"还没有自定义前缀，点击下方编辑":"没有统计项，试试清空基础筛选",14,MUTED,false);noItems.setGravity(Gravity.CENTER);area.addView(noItems,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(noItems);
        BaseAdapter bucketAdapter=new BaseAdapter(){public int getCount(){return displayed.size();}public Object getItem(int i){return displayed.get(i);}public long getItemId(int i){return i;}public View getView(int i,View reuse,ViewGroup parent){SmsSearch.Bucket bucket=displayed.get(i);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(12),dp(8),dp(12));row.setMinimumHeight(dp(52));TextView label=text(bucket.label,15,INK,true);label.setMaxLines(2);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.addView(text(bucket.count+" 条  ›",14,BLUE,true));row.setContentDescription(bucket.label+"，"+bucket.count+"条，点击筛选");return row;}};
        list.setAdapter(bucketAdapter);
        find.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){String q=SmsSearch.normalize(s.toString());displayed.clear();for(SmsSearch.Bucket item:source)if(SmsSearch.normalize(item.label).contains(q))displayed.add(item);bucketAdapter.notifyDataSetChanged();}public void afterTextChanged(android.text.Editable e){}});
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(kind==1?"号码统计":kind==2?"自定义前缀统计":"签名统计").setView(panel).setNegativeButton("关闭",null).setPositiveButton("规则",(d,w)->showSearchRules());
        if(kind==2)builder.setNeutralButton("编辑前缀",(d,w)->editPrefixes());
        final AlertDialog dialog=builder.create();list.setOnItemClickListener((parent,view,position,id)->{SmsSearch.Bucket bucket=displayed.get(position);facetType=kind;facetKey=bucket.key;dialog.dismiss();load(true);});dialog.show();
    }
    void editPrefixes(){
        EditText input=new EditText(this);input.setMinLines(3);input.setMaxLines(6);input.setText(SafetyState.prefs(this).getString("custom_prefixes","106\n955"));input.setHint("例如：106, 955, +86\n最多30项，逗号/分号/换行分隔");input.setContentDescription("自定义发件号前缀列表");input.setPadding(dp(20),dp(12),dp(20),dp(12));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("设置号码前缀").setMessage("前缀匹配号码开头，不自动去国家码。重叠前缀会重复计数；保存后重新统计并清空选择。").setView(input).setPositiveButton("保存并统计",null).setNegativeButton("取消",null).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{List<String> values=SmsSearch.parsePrefixes(input.getText().toString());SafetyState.prefs(this).edit().putString("custom_prefixes",String.join("\n",values)).apply();if(facetType==2){facetType=0;facetKey=null;}dialog.dismiss();load(true);}catch(IllegalArgumentException e){input.setError(e.getMessage());}});
    }
    void confirmDelete(){
        if(inputsChanged()){error("筛选条件已改变，尚未按新条件查询。请先点击查询，再核对所选短信；没有执行删除。");return;}
        if(SafetyState.prefs(this).getBoolean("role_request_pending",false)){error("正在核对系统角色，尚未执行删除，请稍候。");return;}
        if(locked()||selection.isEmpty())return;hideKeyboard();
        if(!isDefault()){new AlertDialog.Builder(this).setTitle("第 1 步 · 临时授权").setMessage("Android 只允许默认短信应用删除系统短信。此工具不是完整短信客户端，不支持 MMS、RCS、发送和快捷回复。默认期间收信可能中断，彩信不会下载或补收。\n\n请先备份，开启飞行模式，清理后恢复原短信应用。授权只完成准备，不会自动删除；选择将保留。退出应用不会恢复默认角色。").setNegativeButton("取消",null).setPositiveButton("申请默认角色",(d,w)->{
            requestSmsRole();}).show();return;}
        if(!airplane()){showAirplane();return;}
        final long[] ids=selection.snapshot();new AlertDialog.Builder(this).setTitle("永久删除 "+ids.length+" 条短信？").setMessage("范围："+scopeDescription+"；"+facetDescription()+"。\n已查询条件：正文 "+(appliedKeyword.isEmpty()?"不限":shortFilter(appliedKeyword))+"；号码 "+(appliedSender.isEmpty()?"不限":shortFilter(appliedSender)+(appliedExact?"（精确）":"（包含）"))+"。\n选择可能跨越所有页。仅按已选 ID 处理，不删除整个会话。系统删除无法撤销，请先备份；删除后逐条核对结果并显示失败或未确认数量。").setNegativeButton("取消",null).setNeutralButton("先备份",(d,w)->export()).setPositiveButton("确认删除",(d,w)->{
            if(!isDefault()||!airplane()){error("权限或飞行模式已改变，尚未开始删除。请重新检查。");return;}
            if(!CleanupTask.start(this,ids)){error("无法启动删除：已有任务进行中，或本地操作日志保存失败。尚未启动新的删除。");return;}observedRunning=true;update();}).show();
    }
    void requestSmsRole(){
        if(!airplane()){showAirplane();return;}
        if(isDefault()){error("已核验当前持有默认短信角色，尚未删除。请再次核对所选短信后确认删除。");return;}
        SafetyState.rememberOriginal(this);
        RoleManager rm=getSystemService(RoleManager.class);
        try {
            if(rm==null||!rm.isRoleAvailable(RoleManager.ROLE_SMS)){showRoleUnavailable("系统未提供可用的短信角色申请入口。");return;}
            SafetyState.prefs(this).edit().putBoolean("role_request_pending",true).putString("role_callback","等待系统回调").apply();
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS),20);
        } catch(RuntimeException e){SafetyState.prefs(this).edit().putBoolean("role_request_pending",false).apply();showRoleUnavailable("无法打开系统申请页面（"+e.getClass().getSimpleName()+"）。");}
    }
    void checkRoleResult(){
        if(!resumed||isFinishing()||isDestroyed()||!SafetyState.prefs(this).getBoolean("role_request_pending",false))return;
        if(isDefault()){
            SafetyState.prefs(this).edit().putBoolean("role_request_pending",false).apply();SafetyState.refreshNotification(this);refreshSafety();
            error("已核验默认短信角色，但尚未删除。已选短信会保留，请再次点击“删除所选”并确认。清理后请恢复原短信应用。");return;
        }
        // OEM role and provider state may settle after the callback. Retry only reads, never writes.
        if(roleAttempts++<6){ui.postDelayed(roleCheck,500);return;}
        SafetyState.prefs(this).edit().putBoolean("role_request_pending",false).apply();SafetyState.refreshNotification(this);refreshSafety();
        String callback=SafetyState.prefs(this).getString("role_callback","");
        showRoleUnavailable(callback.startsWith("RESULT_OK")?"系统返回授权成功，但再次核验仍未确认一致的默认短信状态。为安全起见，尚未删除。":"当前未能确认默认短信角色，没有执行删除。取消申请、系统限制或状态读取异常都可能导致此结果。");
    }
    void showRoleUnavailable(String reason){
        if(!resumed||isFinishing()||isDestroyed())return;
        new AlertDialog.Builder(this).setTitle("请核对默认短信设置").setMessage(reason+"\n\n"+SafetyState.diagnostics(this)+"\n\n请在系统设置中选择“短信清理”作为临时默认短信应用，再返回核对。若系统不允许，不要绕过安全限制。收信/MMS 风险仍存在，清理后必须恢复原应用。")
            .setPositiveButton("打开默认应用设置",(d,w)->{SafetyState.prefs(this).edit().putBoolean("role_request_pending",true).putString("role_callback","从系统设置返回待核对").apply();openSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);})
            .setNeutralButton("复制诊断",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("短信角色诊断",SafetyState.diagnostics(this)));Toast.makeText(this,"已复制状态诊断，不含短信内容",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消",null).show();
    }
    boolean airplane(){return Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)==1;}
    void showAirplane(){new AlertDialog.Builder(this).setTitle("尚未开始删除").setMessage("请先开启飞行模式，再回来继续。选择会保留。飞行模式只能降低风险，不能保证收信不受影响。").setPositiveButton("打开飞行模式设置",(d,w)->openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)).setNegativeButton("取消",null).show();}
    void export(){if(inputsChanged()){error("筛选条件已改变，请先查询并核对选择，再导出备份。");return;}if(locked()||selection.isEmpty())return;exportIds=selection.snapshot();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"sms-backup-"+System.currentTimeMillis()+".json"),30);}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==20){SafetyState.prefs(this).edit().putString("role_callback",result==RESULT_OK?"RESULT_OK（系统返回授予）":"RESULT_CANCELED / "+result).putBoolean("role_request_pending",true).apply();roleAttempts=0;ui.removeCallbacks(roleCheck);if(resumed)ui.post(roleCheck);return;}
        if(request==30&&result==RESULT_OK&&data!=null&&data.getData()!=null){if(exportIds==null){error("备份期间进程被重建，尚未导出。请重新选择并备份。");return;}final Uri target=data.getData();final long[] ids=exportIds;exportIds=null;exporting=true;observedExport=true;SafetyState.prefs(this).edit().putLong("export_id",Math.max(System.currentTimeMillis(),SafetyState.prefs(this).getLong("export_id",0)+1)).putBoolean("export_running",true).putString("export_result","").apply();update();worker.execute(()->{String message;int exported=0;
            try(OutputStream out=getContentResolver().openOutputStream(target)){
                if(out==null)throw new IOException();
                try(android.util.JsonWriter writer=new android.util.JsonWriter(new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8)));Cursor c=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type"},null,null,"date DESC, _id DESC")){
                    if(c==null)throw new IOException();writer.beginObject().name("format").value("sms-manager-backup-v1").name("messages").beginArray();
                    while(c.moveToNext()){if(Arrays.binarySearch(ids,c.getLong(0))<0)continue;SmsRecord record=readSms(c);writer.beginObject().name("id").value(record.id).name("address").value(record.address).name("body").value(record.body).name("date").value(record.date).name("type").value(record.type).endObject();exported++;}
                    writer.endArray().endObject();writer.flush();
                }
                message="已导出 "+exported+" / 已选 "+ids.length+" 条。"+(exported==ids.length?"":"部分已选短信不再可读取，请核对后再删除。")+"\nJSON 可保留和查看，本版本不支持恢复导入，请妥善保管。";
            }catch(Exception e){message="备份未成功完成（"+e.getClass().getSimpleName()+"）。目标文件可能只写入部分内容，请勿据此删除重要短信。";}final String msg=message;SafetyState.prefs(getApplicationContext()).edit().putBoolean("export_running",false).putString("export_result",msg).commit();exporting=false;ui.post(()->{if(isDestroyed())return;update();showExportCompletion();load(false);});});}
        else if(request==30){exportIds=null;if(result==RESULT_OK)error("文件选择器未返回有效保存位置，尚未导出内容。请重试备份。");}
    }
    @Override public void onDestroy(){super.onDestroy();ui.removeCallbacks(monitor);ui.removeCallbacks(roleCheck);generation++;if(querySignal!=null)querySignal.cancel();worker.shutdown();}
}
