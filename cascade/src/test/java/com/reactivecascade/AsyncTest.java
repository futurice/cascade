package com.reactivecascade;

import android.content.Context;
import android.content.res.Resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static junit.framework.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class AsyncTest {
    @Mock
    Resources resources;

    @Mock
    Context context;

    @Before
    public void setUp() {
//        when(context.getResources()).thenReturn(resources);
    }

    @Test
    public void test() {
        assertTrue(true);
    }
}
