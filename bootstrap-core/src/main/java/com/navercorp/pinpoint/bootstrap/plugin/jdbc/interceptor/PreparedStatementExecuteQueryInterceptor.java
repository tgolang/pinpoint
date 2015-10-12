/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.bootstrap.plugin.jdbc.interceptor;

import java.util.HashMap;
import java.util.Map;

import com.navercorp.pinpoint.bootstrap.context.DatabaseInfo;
import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.ParsingResult;
import com.navercorp.pinpoint.bootstrap.context.SpanEventRecorder;
import com.navercorp.pinpoint.bootstrap.context.Trace;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.annotation.TargetMethod;
import com.navercorp.pinpoint.bootstrap.interceptor.annotation.TargetMethods;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.BindValueAccessor;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.DatabaseInfoAccessor;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.ParsingResultAccessor;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.UnKnownDatabaseInfo;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.bindvalue.BindValueUtils;

/**
 * @author emeroad
 */
@TargetMethods({
        @TargetMethod(name="execute"),
        @TargetMethod(name="executeQuery"),
        @TargetMethod(name="executeUpdate")
})
public class PreparedStatementExecuteQueryInterceptor implements AroundInterceptor {

    private static final int DEFAULT_BIND_VALUE_LENGTH = 1024;

    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private final MethodDescriptor descriptor;
    private final TraceContext traceContext;
    private final int maxSqlBindValueLength;
    
    
    public PreparedStatementExecuteQueryInterceptor(TraceContext traceContext, MethodDescriptor descriptor) {
        this(traceContext, descriptor, DEFAULT_BIND_VALUE_LENGTH);
    }
    
    public PreparedStatementExecuteQueryInterceptor(TraceContext traceContext, MethodDescriptor descriptor, int maxSqlBindValueLength) {
        this.traceContext = traceContext;
        this.descriptor = descriptor;
        this.maxSqlBindValueLength = maxSqlBindValueLength;
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        Trace trace = traceContext.currentTraceObject();
        if (trace == null) {
            return;
        }

        SpanEventRecorder recorder = trace.traceBlockBegin();
        try {
            DatabaseInfo databaseInfo = (target instanceof DatabaseInfoAccessor) ? ((DatabaseInfoAccessor)target)._$PINPOINT$_getDatabaseInfo() : null;
            
            if (databaseInfo == null) {
                databaseInfo = UnKnownDatabaseInfo.INSTANCE;
            }
            
            recorder.recordServiceType(databaseInfo.getExecuteQueryType());
            recorder.recordEndPoint(databaseInfo.getMultipleHost());
            recorder.recordDestinationId(databaseInfo.getDatabaseId());

            ParsingResult parsingResult = null;
            if (target instanceof ParsingResultAccessor) {
                parsingResult = ((ParsingResultAccessor)target)._$PINPOINT$_getParsingResult();
            }
            Map<Integer, String> bindValue = null;
            if (target instanceof BindValueAccessor) {
                bindValue = ((BindValueAccessor)target)._$PINPOINT$_getBindValue();
            }
            if (bindValue != null) {
                String bindString = toBindVariable(bindValue);
                recorder.recordSqlParsingResult(parsingResult, bindString);
            } else {
                recorder.recordSqlParsingResult(parsingResult);
            }

            recorder.recordApi(descriptor);
//            trace.recordApi(apiId);
            
            // Need to change where to invoke clean().
            // There is cleanParameters method but it's not necessary to intercept that method.
            // iBatis intentionally does not invoke it in most cases. 
            clean(target);


        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn(e.getMessage(), e);
            }
        }

    }

    private void clean(Object target) {
        if (target instanceof BindValueAccessor) {
            ((BindValueAccessor)target)._$PINPOINT$_setBindValue(new HashMap<Integer, String>());
        }
    }

    private String toBindVariable(Map<Integer, String> bindValue) {
        return BindValueUtils.bindValueToString(bindValue, maxSqlBindValueLength);
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logger.afterInterceptor(target, args, result, throwable);
        }

        Trace trace = traceContext.currentTraceObject();
        if (trace == null) {
            return;
        }

        
        try {
            SpanEventRecorder recorder = trace.currentSpanEventRecorder();
            // TODO Test if it's success. if failed terminate. else calculate resultset fetch too. we'd better make resultset fetch optional.
            recorder.recordException(throwable);
        } finally {
            trace.traceBlockEnd();
        }
    }
}
