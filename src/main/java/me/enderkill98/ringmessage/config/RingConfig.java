package me.enderkill98.ringmessage.config;

import me.enderkill98.ringmessage.util.JSONConfigUtil;

import java.util.HashSet;

public class RingConfig {

    private static final String CONFIG_NAME = "ring.json";
    private static RingConfig configInstance = null;

    public static RingConfig getInstance() {
        if(configInstance == null) configInstance = JSONConfigUtil.load(RingConfig.class, CONFIG_NAME);
        return configInstance;
    }

    public boolean save() {
        return JSONConfigUtil.save(this, CONFIG_NAME);
    }

    public HashSet<String> ringMembers = new HashSet<>();

}
