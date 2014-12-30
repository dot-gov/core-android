package com.android.syssetup.conf;

import org.json.JSONObject;

public class ChildConf extends JSONConf {

    public ChildConf(JSONObject params) {
        super("child", params);
    }

    public String getId() {
        return "child";
    }
    
}