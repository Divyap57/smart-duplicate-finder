package com.cleaner.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class Json {
    public static final Gson G = new GsonBuilder().disableHtmlEscaping().create();

    private Json() { }
}
