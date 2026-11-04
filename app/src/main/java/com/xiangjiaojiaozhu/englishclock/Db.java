package com.xiangjiaojiaozhu.englishclock;
import android.content.*; import android.database.*; import android.database.sqlite.*; import java.util.*;
public class Db extends SQLiteOpenHelper {
  Db(Context c){super(c,"english_clock.db",null,1);} public void onCreate(SQLiteDatabase d){
    d.execSQL("create table article(id integer primary key autoincrement,title text not null,body text not null,added text not null)");
    d.execSQL("create table vocab(id integer primary key autoincrement,word text not null unique,zh text default '',context text default '',source text default '',added text not null,mastered integer default 0)");
  } public void onUpgrade(SQLiteDatabase d,int o,int n){}
  long addArticle(String t,String b){ContentValues v=new ContentValues();v.put("title",t);v.put("body",b);v.put("added",today());return getWritableDatabase().insert("article",null,v);}
  ArrayList<String[]> articles(){ArrayList<String[]> a=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("select id,title,body,added from article order by id desc",null);while(c.moveToNext())a.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3)});c.close();return a;}
  void deleteArticle(long id){getWritableDatabase().delete("article","id=?",new String[]{""+id});}
  boolean hasWord(String w){Cursor c=getReadableDatabase().rawQuery("select 1 from vocab where lower(word)=lower(?)",new String[]{w});boolean x=c.moveToFirst();c.close();return x;}
  void toggleWord(String w,String ctx,String src){if(hasWord(w))getWritableDatabase().delete("vocab","lower(word)=lower(?)",new String[]{w});else{ContentValues v=new ContentValues();v.put("word",w.toLowerCase());v.put("context",ctx);v.put("source",src);v.put("added",today());getWritableDatabase().insert("vocab",null,v);}}
  ArrayList<String[]> vocab(String q){ArrayList<String[]> a=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("select id,word,zh,context,source,added,mastered from vocab where word like ? or zh like ? order by mastered,id desc",new String[]{"%"+q+"%","%"+q+"%"});while(c.moveToNext())a.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6)});c.close();return a;}
  void updateZh(long id,String zh){ContentValues v=new ContentValues();v.put("zh",zh);getWritableDatabase().update("vocab",v,"id=?",new String[]{""+id});}
  void toggleMaster(long id,boolean m){ContentValues v=new ContentValues();v.put("mastered",m?1:0);getWritableDatabase().update("vocab",v,"id=?",new String[]{""+id});}
  void deleteWord(long id){getWritableDatabase().delete("vocab","id=?",new String[]{""+id});}
  static String today(){return new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(new java.util.Date());}
}
