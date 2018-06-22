package com.android.barracuda.model;



public class Status{
    public String text;
    public boolean isOnline;
    public long timestamp;

    public Status(){
        text = "";
        isOnline = false;
        timestamp = 0;
    }
}
