package cn.zerokirby.note.activity;

import static cn.zerokirby.note.MyApplication.getContext;
import static cn.zerokirby.note.userutil.SystemUtil.isMobile;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import cn.zerokirby.note.R;
import cn.zerokirby.note.data.AvatarDataHelper;
import cn.zerokirby.note.data.NoteDataHelper;
import cn.zerokirby.note.data.UserDataHelper;
import cn.zerokirby.note.noteutil.Note;
import cn.zerokirby.note.noteutil.NoteAdapter;
import cn.zerokirby.note.noteutil.NoteAdapterSpecial;
import cn.zerokirby.note.noteutil.NoteChangeConstant;
import cn.zerokirby.note.userutil.IconUtil;
import cn.zerokirby.note.userutil.UriUtil;
import cn.zerokirby.note.userutil.User;

public class MainActivity extends BaseActivity {

    public List<Note> noteList;
    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private NoteAdapter noteAdapter;
    private NoteAdapterSpecial noteAdapterSpecial;
    private Animation adapterAlpha1;//动画1，消失
    private Animation adapterAlpha2;//动画2，出现
    private String searchText;//用来保存在查找对话框输入的文字

    private LocalReceiver localReceiver;
    private LocalBroadcastManager localBroadcastManager;

    private final static int GRID = 0;//网格
    private final static int LIST = 1;//列表
    private static int arrangement = GRID;//排列方式，1为列表

    private final static int SC = 1;//服务器同步到客户端
    private final static int CS = 2;//客户端同步到服务器
    private final static int UPLOAD = 3;//上传图片
    private final static int CHOOSE_PHOTO = 4;//选择图片
    private final static int PHOTO_REQUEST_CUT = 5;//请求裁剪图片
    private long exitTime = 0;//实现再按一次退出的间隔时间

    private NavigationView navigationView;//左侧布局
    private View headView;//头部布局
    private DrawerLayout drawerLayout;//侧滑菜单的三横
    private SwipeRefreshLayout swipeRefreshLayout;//下拉刷新

    private int isLogin;

    private TextView userId;
    private TextView username;
    private TextView lastUse;
    private TextView lastSync;
    private ImageView avatar;

    private Handler handler;
    private IconUtil iconUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //获取动画
        adapterAlpha1 = AnimationUtils.loadAnimation(getContext(), R.anim.adapter_alpha1);
        adapterAlpha2 = AnimationUtils.loadAnimation(getContext(), R.anim.adapter_alpha2);

        //获取recyclerView
        recyclerView = findViewById(R.id.recyclerView);
        noteList = new ArrayList<>();
        noteAdapter = new NoteAdapter(this, noteList);//初始化适配器
        noteAdapterSpecial = new NoteAdapterSpecial(this, noteList);//初始化适配器Special
        layoutManager = new GridLayoutManager(this, 1);//初始化布局管理器
        if (arrangement == GRID) {
            if (isMobile())//手机模式
                layoutManager.setSpanCount(2);//设置列数为2
            else//平板模式
                layoutManager.setSpanCount(3);//设置列数为3
            recyclerView.setAdapter(noteAdapter);//设置适配器
        } else if (arrangement == LIST) {
            layoutManager.setSpanCount(1);//设置列数为1
            recyclerView.setAdapter(noteAdapterSpecial);//设置适配器
        }
        recyclerView.setLayoutManager(layoutManager);//设置笔记布局

        //注册本地广播监听器
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("cn.zerokirby.note.LOCAL_BROADCAST");
        localReceiver = new LocalReceiver();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(localReceiver, intentFilter);

        //为悬浮按钮设置点击事件
        //悬浮按钮
        FloatingActionButton floatingActionButton = findViewById(R.id.floatButton);//新建笔记按钮
        floatingActionButton.setOnClickListener(view -> {
            Intent intent = new Intent(getContext(), EditingActivity.class);
            intent.putExtra("noteId", 0);//传递0，表示新建
            startActivity(intent);
        });

        //为下拉刷新设置事件
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setColorSchemeColors(getThemeManager().getThemeColorFromId(R.attr.color_nav));
        swipeRefreshLayout.setOnRefreshListener(
                this::refreshDataLayout);

        navigationView = findViewById(R.id.nav_view);
        headView = navigationView.getHeaderView(0);//获取头部布局

        //显示侧滑菜单的三横
        drawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setToolBarTitle(R.string.chinese_name);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);//设置菜单图标

        //初始化ProgressDialog，这里为AlertDialog+ProgressBar
        AlertDialog.Builder progressBuilder = new AlertDialog.Builder(this);//显示查找提示
        progressBuilder.setTitle(R.string.please_wait);
        progressBuilder.setMessage(R.string.syncing);
        ProgressBar progressBar = new ProgressBar(this);
        progressBuilder.setView(progressBar);
        AlertDialog progressDialog = progressBuilder.create();


        //用于异步消息处理
        handler = new Handler(msg -> {
            switch (msg.what) {
                case SC:
                case CS:
                    progressDialog.dismiss();
                    drawerLayout.closeDrawers();
                    Toast.makeText(getContext(), getResources().getString(R.string.sync_successfully), Toast.LENGTH_SHORT).show();//显示解析到的内容
                    UserDataHelper.updateSyncTime();
                    refreshData();
                    break;
                case UPLOAD:
                    Toast.makeText(getContext(), getResources().getString(R.string.upload_successfully), Toast.LENGTH_SHORT).show();//上传头像成功
                    break;
            }
            return false;
        });

        //检查登录状态，确定隐藏哪些文字和按钮
        checkLoginStatus();
        iconUtil = new IconUtil(this, avatar);
        //设置navigationView
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.login_btn) {
                Intent intent = new Intent(getContext(), LoginActivity.class);//启动登录
                startActivity(intent);
            } else if (itemId == R.id.sync_SC) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);//显示同步提示
                builder.setTitle(R.string.warning);
                builder.setMessage(R.string.sync_SC_notice);
                builder.setPositiveButton(R.string.sync, (dialogInterface, i) -> {//点击确定则执行同步操作
                    progressDialog.show();
                    UserDataHelper.sendRequestWithOkHttpSC(handler);//根据已登录的ID发送查询请求
                });
                //什么也不做
                builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                });
                builder.show();
            } else if (itemId == R.id.sync_CS) {
                AlertDialog.Builder builder;
                builder = new AlertDialog.Builder(MainActivity.this);//显示同步提示
                builder.setTitle(R.string.warning);
                builder.setMessage(R.string.sync_CS_notice);
                builder.setPositiveButton(R.string.sync, (dialogInterface, i) -> {//点击确定则执行同步操作
                    progressDialog.show();
                    UserDataHelper.sendRequestWithOkHttpCS(handler);//根据已登录的ID发送查询请求
                });
                //什么也不做
                builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                });
                builder.show();
            } else if (itemId == R.id.settings) {
                Intent intent;
                intent = new Intent(getContext(), SettingsActivity.class);//启动设置
                startActivity(intent);
            } else if (itemId == R.id.exit_login) {
                AlertDialog.Builder builder;
                builder = new AlertDialog.Builder(MainActivity.this);//显示提示
                builder.setTitle(R.string.notice);
                builder.setMessage(R.string.exit_notice);
                builder.setPositiveButton(R.string.exit, (dialogInterface, i) -> {
                    UserDataHelper.exitLogin();
                    Toast.makeText(getContext(), getResources().getString(R.string.exit_login_notice), Toast.LENGTH_SHORT).show();
                    drawerLayout.closeDrawers();
                    checkLoginStatus();//再次检查登录状态，调整按钮的显示状态
                });
                builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {

                });
                builder.show();
            } else if (itemId == R.id.help) {
                Intent intent;
                intent = new Intent(getContext(), GuideActivity.class);//启动引导页
                startActivity(intent);
            }
            return true;
        });

        UserDataHelper.getInfo();
        refreshData();
    }

    //刷新数据
    public void refreshData() {
        refreshData("");
    }

    public void refreshData(String s) {
        recyclerView.startAnimation(adapterAlpha1);
        //初始化笔记数据
        noteList.clear();
        noteList.addAll(NoteDataHelper.initNote(s));

        if (arrangement == GRID)
            noteAdapter.notifyDataSetChanged();//通知adapter更新
        else if (arrangement == LIST)
            noteAdapterSpecial.notifyDataSetChanged();//通知adapterSpecial更新

        checkLoginStatus();//检查登录状态
        recyclerView.startAnimation(adapterAlpha2);
    }

    //刷新数据
    private void refreshDataLayout() {
        new Thread(() -> runOnUiThread(() -> {
            //清空搜索内容
            refreshData();
            searchText = "";
            swipeRefreshLayout.setRefreshing(false);
        })).start();
    }

    //通过id寻找item的下标
    private int findItemIndexById(int id) {
        int index = 0;
        for (Note note : noteList) {
            if (note.getId() == id)
                break;
            index++;
        }
        return index;
    }

    //为noteList添加笔记
    public void addNote(Note note) {
        if (note == null) return;

        note.setFlag(true);//设置添加后状态为展开

        noteList.add(0, note);//将数据插入到noteList头部

        if (arrangement == GRID)
            noteAdapter.notifyItemInserted(0);//通知adapter插入数据到头部
        else if (arrangement == LIST) {
            noteAdapterSpecial.notifyItemInserted(0);//通知adapterSpecial有数据插入到头部
            noteAdapterSpecial.notifyItemChanged(1);//通知adapterSpecial更新1号item，隐藏多余的年月
        }

        recyclerView.scrollToPosition(0);//移动到头部
    }

    //删除noteList的笔记
    public void deleteNoteById(int id) {
        int index = findItemIndexById(id);

        noteList.remove(index);//移除原位置的item

        if (arrangement == GRID)
            noteAdapter.notifyItemRemoved(index);//通知adapter移除原位置的item
        else if (arrangement == LIST) {
            noteAdapterSpecial.notifyItemRemoved(index);//通知adapterSpecial移除原位置item
            noteAdapterSpecial.notifyItemChanged(index);//通知adapterSpecial更新代替原位置的item，显示被隐藏的年月
        }
    }

    //修改noteList的笔记
    public void modifyNote(Note note) {
        if (note == null) return;

        note.setFlag(true);//设置修改后状态为展开

        int index = findItemIndexById(note.getId());

        noteList.remove(index);//移除noteList原位置数据
        noteList.add(0, note);//将数据插入到noteList头部

        if (arrangement == GRID) {
            noteAdapter.notifyItemRemoved(index);//通知adapter移除原位置数据
            noteAdapter.notifyItemInserted(0);//通知adapter有数据插入到头部
        } else if (arrangement == LIST) {
            if (index == 0)
                noteAdapterSpecial.notifyItemChanged(0);//通知adapterSpecial更新0号item
            else {
                noteAdapterSpecial.notifyItemRemoved(index);//通知adapterSpecial移除原位置数据
                noteAdapterSpecial.notifyItemChanged(index);//通知adapterSpecial更新代替原位置的item，显示被隐藏的年月
                noteAdapterSpecial.notifyItemInserted(0);//通知adapterSpecial有数据插入到头部
                noteAdapterSpecial.notifyItemChanged(1);//通知adapterSpecial更新1号item，隐藏多余的年月
            }
        }

        recyclerView.scrollToPosition(0);//移动到头部
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
        } else if (itemId == R.id.search_button) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);//显示查找提示
            builder.setTitle(R.string.notice);
            builder.setMessage(R.string.search_notice);

            LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
            View searchView = layoutInflater.inflate(R.layout.search_view, null);
            EditText searchEt = searchView.findViewById(R.id.search_et);
            if (!TextUtils.isEmpty(searchText)) searchEt.setText(searchText);
            builder.setView(searchView);

            builder.setPositiveButton(R.string.search, (dialogInterface, i) -> {//点击确定则执行查找操作
                searchText = searchEt.getText().toString();
                refreshData(searchText);
                Toast.makeText(getContext(),
                        String.format(getResources().getString(R.string.find_notes), noteList.size()), Toast.LENGTH_SHORT).show();
            });
            //清空搜索信息
            builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> searchText = "");
            builder.show();
        } else if (itemId == R.id.arrangement) {
            if (arrangement == GRID) {
                layoutManager.setSpanCount(1);//设置列数为1
                recyclerView.setAdapter(noteAdapterSpecial);//设置适配器Special
                item.setIcon(R.drawable.ic_view_stream_white_24dp);//设置列表按钮
                arrangement = LIST;
            } else if (arrangement == LIST) {
                if (isMobile())//手机模式
                    layoutManager.setSpanCount(2);//设置列数为2
                else//平板模式
                    layoutManager.setSpanCount(3);//设置列数为3
                recyclerView.setAdapter(noteAdapter);//设置适配器
                item.setIcon(R.drawable.ic_view_module_white_24dp);//设置网格按钮
                arrangement = GRID;
            }
            recyclerView.setLayoutManager(layoutManager);//设置笔记布局

            refreshData(searchText);
        } else if (itemId == R.id.theme) {
            getThemeManager().showSwitchDialog(() -> {
                Intent intent = new Intent(MainActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                return null;
            });
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void checkLoginStatus() {//检查登录状态，以调整文字并确定按钮是否显示
        isLogin = UserDataHelper.getUserInfo().getUserId();
        avatar = headView.findViewById(R.id.user_avatar);

        //实例化TextView，以便填入具体数据
        userId = headView.findViewById(R.id.login_userId);
        username = headView.findViewById(R.id.login_username);
        lastUse = headView.findViewById(R.id.last_login);
        lastSync = headView.findViewById(R.id.last_sync);

        //获取菜单
        Menu menu = navigationView.getMenu();
        if (isLogin == 0) {//用户没有登录

            //设置头像未待添加，并禁用修改头像按钮
            avatar.setImageDrawable(getDrawable(R.drawable.ic_person_add_black_24dp));
            avatar.setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);//显示提示
                builder.setTitle(R.string.notice);
                builder.setMessage(R.string.not_login_notice);
                builder.setPositiveButton(R.string.close, (dialogInterface, i) -> {

                });
                builder.show();
            });

            username.setVisibility(View.GONE);//隐藏“用户名”
            userId.setVisibility(View.GONE);//隐藏“用户ID”
            lastUse.setText(R.string.logout_status);//显示“尚未登陆！”
            lastUse.setTextSize(32);//设置文字大小
            lastSync.setVisibility(View.GONE);//隐藏“上次同步”

            menu.getItem(0).setVisible(true);//显示“登录”
            menu.getItem(1).setVisible(false);//隐藏“同步（服务器->客户端）”
            menu.getItem(2).setVisible(false);//隐藏“同步（客户端->服务器）”
            menu.getItem(4).setVisible(false);//隐藏“退出登录”
        } else {//用户已经登录

            //显示头像，并启用修改头像按钮
            Bitmap icon = AvatarDataHelper.readIcon();
            if (icon != null)
                avatar.setImageBitmap(AvatarDataHelper.readIcon());
            else
                avatar.setImageDrawable(getDrawable(R.drawable.ic_person_add_black_24dp));

            avatar.setOnClickListener(v -> iconUtil.iconClick());

            updateTextView();//更新TextView

            menu.getItem(0).setVisible(false);//隐藏“登录”
            menu.getItem(1).setVisible(true);//显示“同步（服务器->客户端）”
            menu.getItem(2).setVisible(true);//显示“同步（客户端->服务器）”
            menu.getItem(4).setVisible(true);//显示“退出登录”

        }
    }

    //注销广播接收器，关闭数据库
    @Override
    protected void onDestroy() {
        super.onDestroy();
        localBroadcastManager.unregisterReceiver(localReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {//开启Activity并返回结果
        switch (requestCode) {
            case CHOOSE_PHOTO:
                if (resultCode == RESULT_OK) {
                    Toast.makeText(getContext(), getResources().getString(R.string.open_successfully), Toast.LENGTH_SHORT).show();
                    if (data != null) iconUtil.handleImage(data);
                } else
                    Toast.makeText(getContext(), getResources().getString(R.string.operation_cancelled), Toast.LENGTH_SHORT).show();
                break;
            case PHOTO_REQUEST_CUT:
                if (resultCode == RESULT_OK) {
                    iconUtil.displayImage(UriUtil.getPath(iconUtil.getCropImageUri()));
                    iconUtil.uploadImage(handler);
                } else
                    Toast.makeText(getContext(), getResources().getString(R.string.operation_cancelled), Toast.LENGTH_SHORT).show();
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //重写，实现再按一次退出以及关闭抽屉
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {//抽屉打开时先关闭抽屉
                drawerLayout.closeDrawers();
                return true;
            }
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Toast.makeText(getContext(), getString(R.string.exitApp), Toast.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    //自动同步数据
    public void modifySync() {
        int userId = UserDataHelper.getUserInfo().getUserId();//检测用户是否登录
        if (userId != 0) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean modifySync = sharedPreferences.getBoolean("modify_sync", false);
            if (modifySync) {
                //用于异步消息处理
                Handler handler = new Handler(msg -> {
                    if (msg.what == CS) {
                        UserDataHelper.updateSyncTime();
                        updateTextView();
                        Toast.makeText(getContext(), R.string.sync_successfully, Toast.LENGTH_SHORT).show();//显示解析到的内容
                    }
                    return true;
                });
                UserDataHelper.sendRequestWithOkHttpCS(handler);
            }
        }
    }

    private void updateTextView() {//更新TextView显示用户信息
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getString(R.string.formatDate_User), Locale.getDefault());
        User user = UserDataHelper.getUserInfo();
        userId.setVisibility(View.VISIBLE);//显示“用户ID”
        username.setVisibility(View.VISIBLE);//显示“用户名”
        lastSync.setVisibility(View.VISIBLE);//显示“上次同步”
        isLogin = user.getUserId();
        userId.setText(String.format(getResources().getString(R.string.login_userId), user.getUserId()));  //读取ID
        username.setText(String.format(getResources().getString(R.string.login_username), user.getUsername()));  //读取用户名
        lastUse.setText(String.format(getResources().getString(R.string.last_login), simpleDateFormat.format(new Date(user.getLastUse()))));  //读取上次登录时间
        lastUse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);//设置文字大小
        if (user.getLastSync() != 0)//读取上次同步时间
            lastSync.setText(String.format(getResources().getString(R.string.last_sync), simpleDateFormat.format(new Date(user.getLastSync()))));
        else
            lastSync.setText(String.format(getResources().getString(R.string.last_sync), getString(R.string.empty)));
    }

    //使用广播接收器处理笔记更新结果
    class LocalReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int operation_type = intent.getIntExtra("operation_type", 0);
            Note note = intent.getParcelableExtra("note_data");
            int note_id = intent.getIntExtra("note_id", 0);

            if (operation_type != 0) modifySync();

            switch (operation_type) {
                case NoteChangeConstant.ADD_NOTE:
                    addNote(note);
                    break;
                case NoteChangeConstant.DELETE_NOTE_BY_ID:
                    deleteNoteById(note_id);
                    break;
                case NoteChangeConstant.MODIFY_NOTE:
                    modifyNote(note);
                    break;
                case NoteChangeConstant.REFRESH_DATA:
                    refreshData();
                    break;
                case NoteChangeConstant.CHECK_LOGIN_STATUS:
                    checkLoginStatus();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {//授权
        if (requestCode == CHOOSE_PHOTO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iconUtil.openAlbum();
            } else {
                Toast.makeText(getContext(), R.string.without_external_permission, Toast.LENGTH_SHORT).show();
            }
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}