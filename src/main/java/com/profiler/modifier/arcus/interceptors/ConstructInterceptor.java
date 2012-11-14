package com.profiler.modifier.arcus.interceptors;

import com.profiler.context.*;
import com.profiler.interceptor.StaticAfterInterceptor;
import com.profiler.util.MetaObject;
import com.profiler.util.StringUtils;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class ConstructInterceptor implements StaticAfterInterceptor {

    private final Logger logger = Logger.getLogger(ConstructInterceptor.class.getName());
    //    private MetaObject<Integer> asyncTraceId = new MetaObject<Integer>("__setAsyncTraceId", int.class);
    private MetaObject<Object> asyncTraceId = new MetaObject<Object>("__setAsyncTraceId", Object.class);


    @Override
    public void after(Object target, String className, String methodName, String parameterDescription, Object[] args, Object result) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("after " + StringUtils.toString(target) + " " + className + "." + methodName + parameterDescription + " args:" + Arrays.toString(args) + " result:" + result);
        }
        TraceContext traceContext = TraceContext.getTraceContext();
        Trace trace = traceContext.currentTraceObject();
        if (trace == null) {
            return;
        }
        AsyncTrace asyncTrace = trace.createAsyncTrace();
        asyncTrace.setAttachObject(new TimeObject());

//        GlobalCallTrace globalCallTrace = traceContext.getGlobalCallTrace();
//        int asyncId = globalCallTrace.registerTraceObject(asyncTrace);
//
//        asyncTraceId.invoke(target, asyncId);
        asyncTraceId.invoke(target, asyncTrace);
    }
}
