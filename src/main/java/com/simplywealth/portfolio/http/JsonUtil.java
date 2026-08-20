package com.simplywealth.portfolio.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;

/** Shared Gson instance (Section 7.3 - shared JSON helper), with a LocalDate adapter since Gson doesn't handle it natively. */
public class JsonUtil {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() {
                @Override
                public void write(JsonWriter out, LocalDate value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(value.toString()); // ISO yyyy-MM-dd
                    }
                }

                @Override
                public LocalDate read(JsonReader in) throws IOException {
                    return LocalDate.parse(in.nextString());
                }
            })
            .setPrettyPrinting()
            .create();
}
