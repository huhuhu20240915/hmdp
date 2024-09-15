package com.hmdp.utils;

public interface iLock {
    boolean tryLock(String key);

    void releaseLock(String key);

}
