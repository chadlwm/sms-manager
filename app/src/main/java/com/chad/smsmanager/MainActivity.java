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
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Local-only SMS cleanup UI. No role grant or query ever starts deletion automatically. */
public class MainActivity extends Activity {
    static final int INK=0xFF172B40, MUTED=0xFF60758B, BLUE=0xFF246BFE, BG=0xFFF3F6FA, RED=0xFFB73343;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler ui=new Handler(Looper.getMainLooper());
    final ArrayList<Sms> rows=new ArrayList<>();
    final HashSet<Long> selected=new HashSet<>();
    EditText keyword,sender; CheckBox exact;
    TextView status,recovery,operation,empty,selectedLabel;
    Button delete,selectAll,backup,cancel,access; ProgressBar progress; BaseAdapter adapter;
    boolean busy,observedRunning,permissionRequested,refreshPending; int generation;
    ArrayList<Sms> exportSnapshot;
    final Runnable monitor=new Runnable(){public void run(){
        boolean running=CleanupTask.isRunning(); refreshSafety();
        if(observedRunning&&!running)load(false);
        observedRunning=running; showCompletion(); ui.postDelayed(this,400);
    }};
    static class Sms {
        long id,date;String address,body;int type;
        Sms(Cursor c){id=c.getLong(0);address=c.getString(1);body=c.getString(2);date=c.getLong(3);type=c.getInt(4);}
    }
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
        Button settings=button("安全与设置",BLUE,false,v->showSafety());header.addView(settings);spaced(root,header,12);
        LinearLayout filters=card();
        keyword=input("搜索正文，例如：优惠、验证码");spaced(filters,keyword,8);
        sender=input("发件号码或标识，例如：106…");spaced(filters,sender,5);
        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        exact=new CheckBox(this);exact.setText("精确发件人");exact.setTextSize(12);exact.setTextColor(MUTED);exact.setChecked(true);searchRow.addView(exact,new LinearLayout.LayoutParams(0,dp(44),1));
        searchRow.addView(button("重置",MUTED,false,v->{if(!locked()){keyword.setText("");sender.setText("");exact.setChecked(true);load(true);}}));
        Button search=button("查询",BLUE,true,v->{hideKeyboard();load(true);});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(76),dp(44));sp.leftMargin=dp(8);searchRow.addView(search,sp);filters.addView(searchRow);spaced(root,filters,10);
        keyword.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);sender.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        TextView.OnEditorActionListener submit=(v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){hideKeyboard();load(true);return true;}return false;};keyword.setOnEditorActionListener(submit);sender.setOnEditorActionListener(submit);
        recovery=text("",12,MUTED,false);recovery.setPadding(dp(12),dp(10),dp(12),dp(10));recovery.setBackground(shape(0xFFE7EEF9,12));recovery.setOnClickListener(v->showSafety());spaced(root,recovery,8);
        operation=text("",13,INK,false);operation.setPadding(dp(12),dp(10),dp(12),dp(10));operation.setMaxLines(3);operation.setEllipsize(android.text.TextUtils.TruncateAt.END);operation.setBackground(shape(0xFFE7F0FE,12));operation.setOnClickListener(v->showResult());spaced(root,operation,8);
        LinearLayout info=new LinearLayout(this);info.setGravity(Gravity.CENTER_VERTICAL);status=text("准备读取…",13,MUTED,false);info.addView(status,new LinearLayout.LayoutParams(0,dp(38),1));selectAll=button("全选",BLUE,false,v->{if(locked())return;if(!rows.isEmpty()&&selected.size()==rows.size())selected.clear();else for(Sms s:rows)selected.add(s.id);update();});info.addView(selectAll);spaced(root,info,6);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        FrameLayout listArea=new FrameLayout(this);root.addView(listArea,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(8));list.setClipToPadding(false);list.setPadding(0,dp(6),0,dp(8));listArea.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout placeholder=column();placeholder.setGravity(Gravity.CENTER);placeholder.setPadding(dp(20),dp(16),dp(20),dp(16));empty=text("还没有短信结果",17,MUTED,true);empty.setGravity(Gravity.CENTER);placeholder.addView(empty);access=button("允许读取短信",BLUE,true,v->requestRead());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(48));ap.topMargin=dp(16);placeholder.addView(access,ap);listArea.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(placeholder);
        adapter=new BaseAdapter(){public int getCount(){return rows.size();}public Object getItem(int i){return rows.get(i);}public long getItemId(int i){return rows.get(i).id;}
            public View getView(int i,View recycled,ViewGroup parent){
                Sms s=rows.get(i);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.TOP);row.setPadding(dp(10),dp(12),dp(12),dp(12));row.setBackground(shape(selected.contains(s.id)?0xFFE8F0FF:Color.WHITE,16));
                CheckBox check=new CheckBox(MainActivity.this);check.setContentDescription("选择来自 "+s.address+" 的短信");check.setChecked(selected.contains(s.id));check.setEnabled(!locked());row.addView(check,new LinearLayout.LayoutParams(dp(44),dp(44)));
                LinearLayout content=column();row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
                TextView address=text(s.address==null?"未知发件人":s.address,16,INK,true);content.addView(address);
                TextView date=text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(s.date))+(s.type==2?" · 已发送":""),11,MUTED,false);content.addView(date);
                TextView body=text(s.body==null?"（空短信）":s.body,14,INK,false);body.setMaxLines(3);body.setEllipsize(android.text.TextUtils.TruncateAt.END);body.setPadding(0,dp(7),0,dp(6));content.addView(body);
                TextView detail=text("查看全文  ›",12,BLUE,true);detail.setPadding(0,dp(8),0,dp(8));content.addView(detail);detail.setOnClickListener(v->showSms(s));
                check.setOnCheckedChangeListener((v,on)->{if(on)selected.add(s.id);else selected.remove(s.id);row.setBackground(shape(on?0xFFE8F0FF:Color.WHITE,16));updateSelection();});
                content.setOnClickListener(v->{if(!locked())check.setChecked(!check.isChecked());});row.setOnLongClickListener(v->{showSms(s);return true;});return row;
            }};list.setAdapter(adapter);
        LinearLayout bottom=column();bottom.setPadding(0,dp(8),0,0);selectedLabel=text("尚未选择短信",13,INK,true);selectedLabel.setPadding(dp(3),0,0,dp(8));bottom.addView(selectedLabel);
        LinearLayout tools=new LinearLayout(this);backup=button("备份所选",BLUE,false,v->export());delete=button("删除所选",RED,true,v->confirmDelete());weighted(tools,backup);weighted(tools,delete);bottom.addView(tools);
        cancel=button("停止后续删除",RED,false,v->{CleanupTask.cancel();Toast.makeText(this,"已请求停止。已经删除的短信无法撤销。",Toast.LENGTH_LONG).show();});cancel.setVisibility(View.GONE);spaced(bottom,cancel,0);root.addView(bottom);
        if(state!=null){keyword.setText(state.getString("keyword",""));sender.setText(state.getString("sender",""));exact.setChecked(state.getBoolean("exact",true));long[] ids=state.getLongArray("selected");if(ids!=null)for(long id:ids)selected.add(id);permissionRequested=state.getBoolean("permissionRequested",false);}
        setContentView(root);
        if(Intent.ACTION_SENDTO.equals(getIntent().getAction()))error("这是清理工具，不支持发送短信。请在安全与设置中恢复原短信应用后发送。");
    }
    boolean locked(){return busy||CleanupTask.isRunning();}
    boolean isDefault(){return SafetyState.isDefault(this);}
    void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keyword.getWindowToken(),0);keyword.clearFocus();sender.clearFocus();}
    void showSms(Sms s){new AlertDialog.Builder(this).setTitle(s.address==null?"短信详情":s.address).setMessage(s.body).setPositiveButton("关闭",null).setNeutralButton("查此发件人",(d,w)->{if(locked())return;sender.setText(s.address);keyword.setText("");exact.setChecked(true);load(true);}).show();}
    void showSafety(){String event=SafetyState.prefs(this).getString("last_event","");new AlertDialog.Builder(this).setTitle("安全与默认短信应用").setMessage(SafetyState.guidance(this)+"\n\n建议先备份，开启飞行模式后临时授权清理。清理完立即恢复原短信应用，再关闭飞行模式。飞行模式也不能保证收信不受影响。\n\n本工具不支持 MMS、RCS、发送和快捷回复。"+(event.isEmpty()?"":"\n\n接收异常："+event)).setPositiveButton("打开默认应用设置",(d,w)->openSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)).setNeutralButton("本应用权限",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("关闭",null).show();}
    void openSettings(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException e){error("未找到对应设置页面，请手动进入系统设置 → 应用 → 默认应用/权限。");}}
    @Override public void onResume(){super.onResume();if(keyword!=null){SafetyState.refreshNotification(this);refreshSafety();load(false);ui.removeCallbacks(monitor);ui.post(monitor);}}
    @Override public void onPause(){super.onPause();ui.removeCallbacks(monitor);}
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("keyword",keyword.getText().toString());b.putString("sender",sender.getText().toString());b.putBoolean("exact",exact.isChecked());b.putBoolean("permissionRequested",permissionRequested);long[] ids=new long[selected.size()];int i=0;for(long id:selected)ids[i++]=id;b.putLongArray("selected",ids);}
    void refreshSafety(){
        boolean running=CleanupTask.isRunning();recovery.setText(isDefault()?"⚠ 当前仍为默认短信应用 · 清理后请恢复  ›":"本机离线处理 · 删除需临时授权 · 安全说明  ›");recovery.setTextColor(isDefault()?0xFF8B4D0E:MUTED);recovery.setBackground(shape(isDefault()?0xFFFFEBCD:0xFFE7EEF9,12));
        String result=CleanupTask.summary(this);operation.setText(result.isEmpty()?"":result+"  点击查看详情 ›");String outcome=SafetyState.prefs(this).getString("delete_outcome","");boolean failed=outcome.equals("failed")||outcome.equals("partial");operation.setBackground(shape(failed?0xFFFFE8E9:0xFFE7F0FE,12));operation.setTextColor(failed?RED:INK);operation.setVisibility(result.isEmpty()?View.GONE:View.VISIBLE);cancel.setVisibility(running?View.VISIBLE:View.GONE);progress.setVisibility(locked()?View.VISIBLE:View.GONE);updateSelection();
    }
    void showResult(){String result=CleanupTask.summary(this);if(result.isEmpty())return;new AlertDialog.Builder(this).setTitle(CleanupTask.isRunning()?"清理进行中":"清理结果").setMessage(result+"\n\n若未成功，请检查 ColorOS 中本应用短信权限及默认短信应用设置。不能确认厂商拦截原因，不会自动扩大删除范围。\n清理后请恢复原短信应用。").setPositiveButton("知道了",null).setNeutralButton("权限与恢复",(d,w)->showSafety()).show();}
    void showCompletion(){android.content.SharedPreferences p=SafetyState.prefs(this);long id=p.getLong("delete_operation_id",0);if(id==0||CleanupTask.isRunning()||id==p.getLong("delete_seen_id",0))return;p.edit().putLong("delete_seen_id",id).apply();showResult();}
    void updateSelection(){boolean lock=locked();selectedLabel.setText(CleanupTask.isRunning()?CleanupTask.progress(this):selected.isEmpty()?"轻点短信卡片选择 · 长按查看全文":"已选 "+selected.size()+" 条短信");delete.setText("删除所选"+(selected.isEmpty()?"":"（"+selected.size()+"）"));delete.setEnabled(!lock&&!selected.isEmpty());delete.setAlpha(delete.isEnabled()?1f:.45f);backup.setEnabled(!lock&&!selected.isEmpty());backup.setAlpha(backup.isEnabled()?1f:.45f);selectAll.setEnabled(!lock&&!rows.isEmpty());selectAll.setText(!rows.isEmpty()&&selected.size()==rows.size()?"取消全选":"全选");}
    void update(){status.setText(busy?"正在读取短信…":rows.size()+" 条匹配结果");adapter.notifyDataSetChanged();refreshSafety();}
    void error(String value){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("操作提示").setMessage(value).setPositiveButton("知道了",null).show();}
    void requestRead(){if(checkSelfPermission(Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED){load(false);return;}if(permissionRequested&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)){new AlertDialog.Builder(this).setTitle("开启短信读取权限").setMessage("请到本应用权限页面允许读取短信，然后返回重试。部分 ColorOS 版本对侧载应用另有限制。").setPositiveButton("打开权限设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("取消",null).show();return;}permissionRequested=true;requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.READ_SMS,Manifest.permission.POST_NOTIFICATIONS}:new String[]{Manifest.permission.READ_SMS},10);}
    void load(boolean reset){
        if(CleanupTask.isRunning())return;if(reset)selected.clear();if(busy){refreshPending=true;return;}
        if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED){rows.clear();selected.clear();empty.setText("允许读取短信，开始整理\n内容仅在手机内处理");access.setVisibility(View.VISIBLE);update();return;}
        access.setVisibility(View.GONE);empty.setText("正在读取短信…");final int token=++generation;final String k=keyword.getText().toString(),a=sender.getText().toString().trim();final boolean useExact=exact.isChecked()&&!a.isEmpty();busy=true;update();
        worker.execute(()->{ArrayList<Sms> found=new ArrayList<>();String failure=null;
            String clause="COALESCE(body,'') LIKE ? ESCAPE '\\' AND "+(useExact?"address = ?":"COALESCE(address,'') LIKE ? ESCAPE '\\'");
            try(Cursor c=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type"},clause,new String[]{Filters.pattern(k),useExact?a:Filters.pattern(a)},"date DESC, _id DESC")){if(c==null)failure="短信服务未返回数据，请检查读取权限后重试。";else while(c.moveToNext())found.add(new Sms(c));}catch(Exception e){failure="读取失败："+e.getClass().getSimpleName()+"。请检查系统短信权限。";}
            final String message=failure;runOnUiThread(()->{if(isDestroyed()||token!=generation)return;busy=false;rows.clear();rows.addAll(found);HashSet<Long> available=new HashSet<>();for(Sms s:rows)available.add(s.id);selected.retainAll(available);empty.setText(message==null?"没有匹配的短信\n试试更短的关键词或重置筛选":message);update();if(message!=null)error(message);if(refreshPending){refreshPending=false;load(false);}});
        });
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10){load(false);if(g.length==0||g[0]!=PackageManager.PERMISSION_GRANTED)error("未获得读取权限，因此尚未读取或删除短信。可点击“允许读取短信”重试。");}}
    void confirmDelete(){
        if(locked()||selected.isEmpty())return;hideKeyboard();
        if(!isDefault()){new AlertDialog.Builder(this).setTitle("第 1 步 · 临时授权").setMessage("Android 只允许默认短信应用删除系统短信。此工具不是完整短信客户端，不支持 MMS、RCS、发送和快捷回复。默认期间收信可能中断，彩信不会下载或补收。\n\n请先备份，开启飞行模式，清理后恢复原短信应用。授权只完成准备，不会自动删除；选择将保留。退出应用不会恢复默认角色。").setNegativeButton("取消",null).setPositiveButton("申请默认角色",(d,w)->{
            if(!airplane()){showAirplane();return;}SafetyState.rememberOriginal(this);RoleManager rm=getSystemService(RoleManager.class);if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_SMS)){try{startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS),20);}catch(Exception e){error("无法打开默认角色申请，请到系统默认应用设置检查。");}}else error("系统未提供短信角色，请检查默认应用设置。");}).show();return;}
        if(!airplane()){showAirplane();return;}
        final ArrayList<Long> ids=new ArrayList<>(selected);new AlertDialog.Builder(this).setTitle("永久删除 "+ids.size()+" 条短信？").setMessage("仅处理当前选中的短信，不删除整个会话。系统删除无法撤销。建议先备份；删除后会逐条核对结果，并明确显示失败或未确认数量。").setNegativeButton("取消",null).setNeutralButton("先备份",(d,w)->export()).setPositiveButton("确认删除",(d,w)->{
            if(!isDefault()||!airplane()){error("权限或飞行模式已改变，尚未开始删除。请重新检查。");return;}
            if(!CleanupTask.start(this,ids)){error("无法启动删除：已有任务进行中，或本地操作日志保存失败。尚未启动新的删除。");return;}observedRunning=true;update();}).show();
    }
    boolean airplane(){return Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)==1;}
    void showAirplane(){new AlertDialog.Builder(this).setTitle("尚未开始删除").setMessage("请先开启飞行模式，再回来继续。选择会保留。飞行模式只能降低风险，不能保证收信不受影响。").setPositiveButton("打开飞行模式设置",(d,w)->openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)).setNegativeButton("取消",null).show();}
    void export(){if(locked()||selected.isEmpty())return;exportSnapshot=new ArrayList<>();for(Sms s:rows)if(selected.contains(s.id))exportSnapshot.add(s);startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"sms-backup-"+System.currentTimeMillis()+".json"),30);}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==20){error(isDefault()?"默认角色已授予，但尚未删除。请检查已选短信，再次点击“删除所选”并确认。清理后请恢复原短信应用。":"未获得默认短信角色，没有删除任何短信。");return;}
        if(request==30&&result==RESULT_OK&&data!=null&&data.getData()!=null){if(exportSnapshot==null){error("备份页面期间应用被重建，尚未导出内容。请重新选择并备份。");return;}final Uri target=data.getData();final ArrayList<Sms> snapshot=exportSnapshot;exportSnapshot=null;worker.execute(()->{String message;
            try{JSONArray entries=new JSONArray();for(Sms s:snapshot){JSONObject j=new JSONObject();j.put("id",s.id);j.put("address",s.address);j.put("body",s.body);j.put("date",s.date);j.put("type",s.type);entries.put(j);}JSONObject document=new JSONObject();document.put("format","sms-manager-backup-v1");document.put("messages",entries);try(OutputStream out=getContentResolver().openOutputStream(target)){if(out==null)throw new IOException();out.write(document.toString(2).getBytes(StandardCharsets.UTF_8));}message="已备份 "+snapshot.size()+" 条短信。JSON 可保留和查看，但本版本不支持恢复导入。请妥善保管私密内容。";}catch(Exception e){message="备份未成功确认："+e.getClass().getSimpleName()+"。请重试，暂勿删除重要短信。";}final String msg=message;runOnUiThread(()->error(msg));});}
    }
    @Override public void onDestroy(){super.onDestroy();ui.removeCallbacks(monitor);worker.shutdown();}
}
