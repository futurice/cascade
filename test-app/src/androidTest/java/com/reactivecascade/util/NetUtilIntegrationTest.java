/*
This file is part of Reactive Cascade which is released under The MIT License.
See license.md , https://github.com/futurice/cascade and http://reactivecascade.com for details.
This is open source for the common good. Please contribute improvements by pull request or contact paulirotta@gmail.com
*/
package com.reactivecascade.util;

import android.support.annotation.RequiresPermission;
import android.support.test.runner.AndroidJUnit4;

import com.reactivecascade.AsyncBuilder;
import com.reactivecascade.CascadeIntegrationTest;
import com.reactivecascade.functional.SettableAltFuture;
import com.reactivecascade.i.IAltFuture;
import com.reactivecascade.reactive.ReactiveValue;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;

import okhttp3.Response;
import okhttp3.internal.framed.Header;

import static com.reactivecascade.Async.WORKER;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class NetUtilIntegrationTest extends CascadeIntegrationTest {
    private NetUtil netUtil;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        new AsyncBuilder(appContext)
                .setStrictMode(false)
                .build();
        if (netUtil == null) {
            netUtil = new NetUtil(appContext);
        }
        defaultTimeoutMillis = 15000; // Give real net traffic enough time to complete
    }

    @Test
    public void testGet() throws Exception {
        assertTrue(netUtil.get("http://httpbin.org/").body().bytes().length > 100);
    }

    @Test
    public void testGetWithHeaders() throws Exception {
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Test", "ValueZ"));
        assertTrue(netUtil.get("http://httpbin.org/headers", headers).body().string().contains("ValueZ"));
    }

    @Test
    public void testGetFromIGettable() throws Exception {
        ReactiveValue<String> value = new ReactiveValue<>("RV Test", "http://httpbin.org/headers");
        assertTrue(netUtil.get(value).body().bytes().length > 20);
    }

    @Test
    @Ignore //TODO something to do with Async.USE_FORKED_STATE
    public void testGetFromIGettableWithHeaders() throws Exception {
        ReactiveValue<String> value = new ReactiveValue<>("RV Test", "http://httpbin.org/headers");
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Test", "ValueG"));
        assertTrue(netUtil.get(value, headers).body().string().contains("ValueG"));
    }

    @Test
    @Ignore //TODO something to do with Async.USE_FORKED_STATE
    public void testGetAsync() throws Exception {
        IAltFuture<?, Response> iaf = netUtil
                .getAsync("http://httpbin.org/get")
                .then(this::signal)
                .fork();
        awaitSignal();
        assertEquals(HttpURLConnection.HTTP_OK, iaf.get().code());
    }

    @Test
    public void testGetAsyncFrom() throws Exception {
        IAltFuture<?, Response> iaf = WORKER
                .from("http://httpbin.org/get")
                .then(netUtil.getAsync());
        assertTrue(await(iaf).isSuccessful());
    }

    @Test
    public void testGetAsyncWithHeaders() throws Exception {
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Test", "ValueZ"));
        assertTrue(await(
                netUtil
                        .getAsync("http://httpbin.org/headers", headers)
                        .fork()
        ).body().string().contains("ValueZ"));
    }

    @Test
    public void testValueGetAsyncWithHeaders() throws Exception {
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Test", "ValueT"));
        IAltFuture<?, Response> iaf = WORKER
                .from("http://httpbin.org/headers")
                .then(netUtil.getAsync(headers));
        assertTrue(await(iaf).body().string().contains("ValueT"));
    }

    @Test
    public void testGetAsyncFromIGettableWithHeaders() throws Exception {
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Blah", "VaGG"));
        SettableAltFuture<Collection<Header>> altFuture = new SettableAltFuture<>(WORKER);
        altFuture.set(headers);
        IAltFuture<?, Response> iaf = WORKER
                .from("http://httpbin.org/get")
                .then(netUtil.getAsync(altFuture));
        assertTrue(await(iaf).body().string().contains("VaGG"));
    }

    @Test
    public void testPut() throws Exception {

    }

    @Test
    public void testPut1() throws Exception {

    }

    @Test
    public void testPutAsync() throws Exception {

    }

    @Test
    public void testPutAsync1() throws Exception {

    }

    @Test
    public void testPutAsync2() throws Exception {

    }

    @Test
    public void testPutAsync3() throws Exception {

    }

    @Test
    public void testPutAsync4() throws Exception {

    }

    @Test
    public void testPutAsync5() throws Exception {

    }

    @Test
    public void testPost() throws Exception {

    }

    @Test
    public void testPostAsync() throws Exception {

    }

    @Test
    public void testPostAsync1() throws Exception {

    }

    @Test
    public void testPostAsync2() throws Exception {

    }

    @Test
    public void testPost1() throws Exception {

    }

    @Test
    public void testPostAsync3() throws Exception {

    }

    @Test
    public void testPostAsync4() throws Exception {

    }

    @Test
    public void testPostAsync5() throws Exception {

    }

    @Test
    public void testPost2() throws Exception {

    }

    @Test
    public void testDeleteAsync() throws Exception {

    }

    @Test
    public void testDeleteAsync1() throws Exception {

    }

    @Test
    public void testDelete() throws Exception {

    }

    @Test
    public void testDeleteAsync2() throws Exception {

    }

    @Test
    public void testDeleteAsync3() throws Exception {

    }

    @Test
    public void testDelete1() throws Exception {

    }

    @Test
    @RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
    public void testGetMaxNumberOfNetConnections() throws Exception {
        assertTrue(netUtil.getMaxNumberOfNetConnections() > 1);
    }

    @Test
    @RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
    public void testIsWifi() throws Exception {
        assertFalse(netUtil.isWifi());
    }

    @Test
    public void testGetNetworkType() throws Exception {
        NetUtil.NetType netType = netUtil.getNetworkType();
        assertTrue(netType == NetUtil.NetType.NET_4G ||
                netType == NetUtil.NetType.NET_3G ||
                netType == NetUtil.NetType.NET_2_5G ||
                netType == NetUtil.NetType.NET_2G);
    }
}