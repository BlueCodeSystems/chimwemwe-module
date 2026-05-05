package com.bluecodeltd.chimwemwe.chw.api;

import com.bluecodeltd.chimwemwe.chw.configs.Config;
import com.bluecodeltd.chimwemwe.chw.model.ItemList;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ItemApi {

    @GET(Config.ITEMURL)
    Call<ItemList> getItems(@Query("phone") String phone);
}
