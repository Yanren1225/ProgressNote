package cn.zerokirby.note.noteData;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import cn.zerokirby.note.activity.MainActivity;
import cn.zerokirby.note.R;

import static cn.zerokirby.note.MyApplication.getContext;

public class NoteItem implements Parcelable {
    private int id;
    private String title;
    private String body;
    private String date;

    public NoteItem(int id, String title, String body, String date) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.date = date;
    }

    public NoteItem() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    //获取yy年
    public String getYear() {
        return date.substring(0, 5);
    }

    //获取MM月，若为第一位为0，则去掉0
    public String getMonth() {
        String month = date.substring(5, 8);
        if(month.substring(0, 1).equals("0"))
            month = month.substring(1);
        return month;
    }

    //获取dd日，若为第一位为0，则去掉0
    public String getDay() {
        String day = date.substring(8, 11);
        if(day.substring(0, 1).equals("0")) {
            day = day.substring(1);
        }
        return day;
    }

    //获取过去的时间表示
    public String getPassDay() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                getContext().getString(R.string.format_year_month_day), Locale.getDefault());
        Date nowTime = null;
        try {
            nowTime = simpleDateFormat.parse(date);
        } catch(ParseException e) {
            e.printStackTrace();
        }
        long diff = 0;
        if(nowTime != null) diff = System.currentTimeMillis() - nowTime.getTime();
        int days = (int) (diff / (1000 * 60 * 60 * 24));

        if(days == 0)
            return "今天";
        else if(days == 1)
            return "昨天";
        else if(days < 7) {
            Calendar calendar = Calendar.getInstance();
            int weekday = (7 + calendar.get(Calendar.DAY_OF_WEEK) - days) % 7;
            switch (weekday) {
                case 0: return getDay() + " 星期六";
                case 1: return getDay() + " 星期日";
                case 2: return getDay() + " 星期一";
                case 3: return getDay() + " 星期二";
                case 4: return getDay() + " 星期三";
                case 5: return getDay() + " 星期四";
                case 6: return getDay() + " 星期五";
            }
        }
        return getDay();
    }

    //获取HH:mm:ss时 分 秒
    public String getTime() {
        return date.substring(12);
    }

    private boolean flag = false;//这个成员用来记录dataItem的展开状态

    public boolean getFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeString(body);
        dest.writeString(date);
    }

    public static final Parcelable.Creator<NoteItem> CREATOR
            = new Parcelable.Creator<NoteItem>() {
        @Override
        public NoteItem createFromParcel(Parcel source) {
            return new NoteItem(source.readInt(), source.readString(),
                    source.readString(), source.readString());
        }

        @Override
        public NoteItem[] newArray(int size) {
            return new NoteItem[size];
        }
    };
}