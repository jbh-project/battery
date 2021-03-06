/**
 * Copyright (c) 2014 Park Joon-Kyu, All Rights Reserved.
 */

package com.spoqa.battery;


public interface OnResponse<T> {
    public void onResponse(T responseBody);
    public void onFailure(Throwable why);
}
