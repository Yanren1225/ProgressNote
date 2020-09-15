package cn.zerokirby.note.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import cn.zerokirby.note.R;
import cn.zerokirby.note.noteData.NoteItem;
import cn.zerokirby.note.userData.SystemUtil;
import cn.zerokirby.note.userData.User;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static cn.zerokirby.note.MyApplication.getContext;

public class DatabaseOperateUtil {

    private final int SC = 1;//服务器同步到客户端
    private final int CS = 2;//客户端同步到服务器
    private String responseData;
    private int userId;
    private int noteId;
    private long time;
    private String title;
    private String content;
    private DatabaseHelper noteDbHelper;

    public DatabaseOperateUtil() {
        this.noteDbHelper = new DatabaseHelper("ProgressNote.db", null, 1);
        this.userId = getUserInfo().getUserId();
    }

    public void sendRequestWithOkHttpSC(Handler handler) {
        new Thread(new Runnable() {
            @Override
            public void run() {//在子线程中进行网络操作
                try {
                    OkHttpClient client = new OkHttpClient();//利用OkHttp发送HTTP请求调用服务器到客户端的同步servlet
                    RequestBody requestBody = new FormBody.Builder().add("userId", String.valueOf(userId)).build();
                    Request request = new Request.Builder().url("https://zerokirby.cn:8443/progress_note_server/SyncServlet_SC").post(requestBody).build();
                    Response response = client.newCall(request).execute();
                    responseData = Objects.requireNonNull(response.body()).string();
                    parseJSONWithJSONArray(responseData);//处理JSON
                    Message message = new Message();
                    message.what = SC;
                    handler.sendMessage(message);//通过handler发送消息请求toast
                    response.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void sendRequestWithOkHttpCS(Handler handler) {
        new Thread(new Runnable() {
            @Override
            public void run() {//在子线程中进行网络操作
                try {
                    OkHttpClient client = new OkHttpClient();//利用OkHttp发送HTTP请求调用客户端到服务器的同步servlet
                    RequestBody requestBody = new FormBody.Builder().add("userId", String.valueOf(userId))
                            .add("json", Objects.requireNonNull(makeJSONArray())).build();
                    Request request = new Request.Builder().url("https://zerokirby.cn:8443/progress_note_server/SyncServlet_CS").post(requestBody).build();
                    Response response = client.newCall(request).execute();
                    Message message = new Message();
                    message.what = CS;
                    handler.sendMessage(message);//通过handler发送消息请求toast
                    response.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void parseJSONWithJSONArray(String jsonData) {//处理JSON
        try {
            JSONArray jsonArray = new JSONArray(jsonData);
            SQLiteDatabase db = noteDbHelper.getWritableDatabase();
            db.execSQL("Delete from Note");//清空笔记表
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                noteId = jsonObject.getInt("NoteId");
                time = jsonObject.getLong("Time");
                title = jsonObject.getString("Title");
                content = jsonObject.getString("Content");

                ContentValues values = new ContentValues();//将笔记ID、标题、修改时间和内容存储到本地
                values.put("id", noteId);
                values.put("title", title);
                values.put("time", time);
                values.put("content", content);

                db.insert("Note", null, values);
            }
            db.close();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String makeJSONArray() {//生成JSON数组的字符串
        try {
            JSONArray jsonArray = new JSONArray();
            SQLiteDatabase db = noteDbHelper.getReadableDatabase();
            Cursor cursor = db.query("Note", null, null,
                    null, null, null, null,
                    null);//查询对应的数据
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject jsonObject = new JSONObject();

                    jsonObject.put("NoteId", cursor.getString(cursor
                            .getColumnIndex("id")));//读取编号
                    jsonObject.put("Title", cursor.getString(cursor
                            .getColumnIndex("title")));//读取标题
                    jsonObject.put("Time", cursor.getLong(cursor
                            .getColumnIndex("time")));//读取修改时间
                    jsonObject.put("Content", cursor.getString(cursor
                            .getColumnIndex("content")));//读取正文

                    jsonArray.put(jsonObject);
                } while (cursor.moveToNext());
            }
            if (cursor != null) {
                cursor.close();
                db.close();
            }
            return jsonArray.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String[] getLogin() {//获取记住的用户名和密码
        String[] str = new String[2];
        SQLiteDatabase db = noteDbHelper.getReadableDatabase();
        Cursor cursor = db.query("User", new String[]{"username", "password"}, null,
                null, null, null, null,
                null);//查询对应的数据
        if (cursor.moveToFirst()) {
            str[0] = cursor.getString(cursor.getColumnIndex("username"));  //读取用户名
            str[1] = cursor.getString(cursor.getColumnIndex("password"));  //读取密码
        }
        cursor.close();
        db.close();
        return str;
    }

    public void setUserColumnNull(String column) {//将User数据库的某个字段置为空
        SQLiteDatabase db = noteDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.putNull(column);//将该字段置为空
        db.update("User", values, "rowid = ?", new String[]{"1"});
        db.close();
    }

    public void updateSyncTime() {//更新同步时间
        SQLiteDatabase db = noteDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("lastSync", System.currentTimeMillis());
        db.update("User", values, "rowid = ?", new String[]{"1"});
        db.close();
    }

    public void exitLogin() { //退出登录，设置用户ID和上次同步时间为0
        SQLiteDatabase db = noteDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("userId", 0);
        values.put("lastSync", 0);
        db.update("user", values, "rowid = ?", new String[]{"1"});
        db.close();
    }

    public void getInfo() { //获取手机信息
        SQLiteDatabase db = noteDbHelper.getWritableDatabase();
        SystemUtil systemUtil = new SystemUtil();//获取手机信息
        ContentValues values = new ContentValues();
        values.put("language", systemUtil.getSystemLanguage());
        values.put("version", systemUtil.getSystemVersion());
        values.put("display", systemUtil.getSystemDisplay());
        values.put("model", systemUtil.getSystemModel());
        values.put("brand", systemUtil.getDeviceBrand());
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM User", null);
        cursor.moveToFirst();
        long num = cursor.getLong(0);

        if (num == 0) {//如果不存在记录
            values.put("userId", 0);
            db.insert("User", null, values);//插入
        } else
            db.update("User", values, "rowid = ?", new String[]{"1"});//更新
        cursor.close();
        db.close();
    }

    public Bitmap readIcon() { //从数据库中读取头像
        AvatarDatabaseUtil avatarDatabaseUtil = new AvatarDatabaseUtil(noteDbHelper);
        byte[] imgData = avatarDatabaseUtil.readImage();
        if (imgData != null) {
            //将字节数组转化为位图，将位图显示为图片
            return BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
        } else
            return null;
    }

    public User getUserInfo() { //获取用户信息并返回一个User对象
        SQLiteDatabase db = noteDbHelper.getReadableDatabase();
        User user = new User();
        Cursor cursor = db.query("User", null, "rowid = ?",
                new String[]{"1"}, null, null, null,
                null);//查询对应的数据
        if (cursor.moveToFirst()) {
            user.setValid(true); //默认为有效用户
            user.setUserId(cursor.getInt(cursor.getColumnIndex("userId")));  //读取ID
            user.setUsername(cursor.getString(cursor.getColumnIndex("username")));  //读取用户名
            user.setPassword(cursor.getString(cursor.getColumnIndex("password")));  //读取密码
            user.setLanguage(cursor.getString(cursor.getColumnIndex("language"))); //读取语言
            user.setVersion(cursor.getString(cursor.getColumnIndex("version")));  //读取版本
            user.setDisplay(cursor.getString(cursor.getColumnIndex("display")));  //读取显示信息
            user.setModel(cursor.getString(cursor.getColumnIndex("model")));  //读取型号
            user.setBrand(cursor.getString(cursor.getColumnIndex("brand")));  //读取品牌
            user.setRegisterTime(cursor.getLong(cursor.getColumnIndex("registerTime")));  //读取注册时间
            user.setLastUse(cursor.getLong(cursor.getColumnIndex("lastUse")));  //读取上次登录时间
            user.setLastSync(cursor.getLong(cursor.getColumnIndex("lastSync")));  //读取上次同步时间
        }
        cursor.close();
        db.close();
        return user;
    }

    //初始化从数据库中读取数据并填充dataItem
    public List<NoteItem> initData(String s) {
        List<NoteItem> dataList = new ArrayList<>();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                getContext().getString(R.string.formatDate), Locale.getDefault());
        SQLiteDatabase db = noteDbHelper.getReadableDatabase();
        Cursor cursor = db.query("Note", null, null,
                null, null, null, "time desc",
                null);//查询对应的数据

        if(cursor != null && cursor.moveToFirst()) {
            do {
                String title = cursor.getString(cursor.getColumnIndex("title"));//读取标题并存入title
                String time = simpleDateFormat.format(new Date(cursor.getLong(
                        cursor.getColumnIndex("time"))));//读取时间并存入time
                String content = cursor.getString(cursor.getColumnIndex("content"));////读取文本并存入content

                //如果字符串为空 或 标题、时间（年月日）或文本中包含要查询的字符串
                if(TextUtils.isEmpty(s) || (title + time.substring(0, 11) + content).contains(s)) {
                    //封装数据
                    NoteItem noteItem = new NoteItem();
                    noteItem.setId(Integer.parseInt(cursor.getString(cursor
                            .getColumnIndex("id"))));//读取编号，需从字符串型转换成整型
                    noteItem.setTitle(title);
                    noteItem.setDate(time);
                    noteItem.setBody(content);
                    dataList.add(noteItem);
                }
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        db.close();
        return dataList;//返回笔记数据集
    }

}
