/**
 * Copyright (c) 2014 Park Joon-Kyu, All Rights Reserved.
 */

package com.spoqa.battery.transformers;

import com.spoqa.battery.FieldNameTransformer;
import com.spoqa.battery.StringUtils;

import java.util.Arrays;
import java.util.List;

public class UnderscoreNameTransformer implements FieldNameTransformer {

    @Override
    public List<String> decode(String key) {
        return Arrays.asList(key.split("_"));
    }

    @Override
    public String encode(List<String> parts) {
        return StringUtils.join(parts, "_", StringUtils.toLowerTransformer);
    }
}
