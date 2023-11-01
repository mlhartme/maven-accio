package net.oneandone.maven.summon.extension;

import java.util.ArrayList;
import java.util.List;

public class Restriction {
    /** null: allow all; empty: deny all; otherwise: allow keys */
    private List<String> keys;

    /** default is to deny all */
    public Restriction() {
        this.keys = new ArrayList<>();
    }

    public boolean isAllowed(String key) {
        return keys == null || keys.contains(key);
    }

    public Restriction allowProperty(String propertyName) {
        String property = System.getProperty(propertyName);
        if (property != null) {
            for (String entry : property.split(",")) {
                if (!entry.isBlank()) {
                    keys.add(entry.trim());
                }
            }
        }
        return this;
    }


    public void allow(String key) {
        if (keys == null) {
            keys = new ArrayList<>();
        }
        keys.add(key);
    }

    public void allowAll() {
        this.keys = null;
    }
    public void denyAll() {
        this.keys = new ArrayList<>();
    }

    public void add(Restriction other) {
        if (other.keys == null) {
            this.keys = null;
        } else {
            for (String key : other.keys) {
                allow(key);
            }
        }
    }
}
