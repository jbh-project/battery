/**
 * Copyright (c) 2014 Park Joon-Kyu, All Rights Reserved.
 */

package com.spoqa.battery.android;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;

import com.spoqa.battery.Config;
import com.spoqa.battery.ExecutionContext;
import com.spoqa.battery.FieldNameTranslator;
import com.spoqa.battery.HttpRequest;
import com.spoqa.battery.Logger;
import com.spoqa.battery.ObjectBuilder;
import com.spoqa.battery.OnResponse;
import com.spoqa.battery.RequestFactory;
import com.spoqa.battery.annotations.RpcObject;
import com.spoqa.battery.exceptions.ContextException;
import com.spoqa.battery.exceptions.DeserializationException;
import com.spoqa.battery.exceptions.ResponseValidationException;
import com.spoqa.battery.exceptions.RpcException;
import com.spoqa.battery.exceptions.SerializationException;

import java.io.UnsupportedEncodingException;

import rx.Observable;
import rx.subjects.PublishSubject;

public class AndroidExecutionContext extends ExecutionContext<Context> {

    private static final String TAG = "AndroidExecutionContext";

    private RequestQueue mRequestQueue;
    private Context mAndroidContext;

    public AndroidExecutionContext(Context androidApplicationContext, RequestQueue requestQueue) {
        super();
        mAndroidContext = androidApplicationContext;
        mRequestQueue = requestQueue;

        /* set up android logger */
        Logger.registerLogger(new AndroidLogger());
    }

    public AndroidExecutionContext(Context androidApplicationContext) {
        this(androidApplicationContext,
                Volley.newRequestQueue(androidApplicationContext, new OkHttpStack()));
    }

    public RequestQueue getRequestQueue() {
        return mRequestQueue;
    }

    public Context androidApplicationContext() {
        return mAndroidContext;
    }

    public <T> void invokeAsync(final T rpcObject, final OnResponse<T> onResponse) {
        HttpRequest request = null;
        try {
            request = RequestFactory.createRequest(this, rpcObject);
        } catch (SerializationException e) {
            onResponse.onFailure(e);
            return;
        } catch (ContextException e) {
            onResponse.onFailure(e.why());
            return;
        }

        if (request == null) {
            Logger.error(TAG, "Could not make call due to error(s) while creating request object.");
            return;
        }

        final RpcObject rpcObjectDecl = rpcObject.getClass().getAnnotation(RpcObject.class);
        final FieldNameTranslator nameTranslator = request.getFieldNameTranslator();
        Response.Listener<ResponseDelegate> onVolleyResponse = new Response.Listener<ResponseDelegate>() {
            @Override
            public void onResponse(ResponseDelegate s) {
                try {
                    /* force content type if declared by RpcObject */
                    String contentType = rpcObjectDecl.expectedContentType();
                    if (contentType == null || contentType.length() == 0)
                        contentType = s.contentType();
                    ObjectBuilder.build(AndroidExecutionContext.this, contentType, s.data(),
                            rpcObject, nameTranslator);

                    if (getResponseValidator() != null) {
                        try {
                            getResponseValidator().validate(rpcObject);
                        } catch (ResponseValidationException e) {
                            if (!dispatchErrorHandler(mAndroidContext, e)) {
                                onResponse.onFailure(e);
                                return;
                            }
                        }
                    }

                    onResponse.onResponse(rpcObject);
                } catch (DeserializationException e) {
                    if (!dispatchErrorHandler(mAndroidContext, e))
                        onResponse.onFailure(e);
                }
            }
        };

        Response.ErrorListener onVolleyErrorResponse = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Logger.error(TAG, "Error while RPC call: " + volleyError.getMessage());
                if (Config.DEBUG_DUMP_RESPONSE) {
                    try {
                        if (volleyError.networkResponse != null)
                            Logger.error(TAG, "Error response: " +
                                    new String(volleyError.networkResponse.data, "utf-8"));
                    } catch (UnsupportedEncodingException e) {}
                }

                Throwable e = volleyError.getCause();
                if (e == null) {
                    if (volleyError instanceof ServerError) {
                        e = new RpcException("Server Error");
                    } else {
                        e = new RpcException(volleyError.toString());
                    }
                }
                if (!dispatchErrorHandler(mAndroidContext, volleyError) &&
                        !dispatchErrorHandler(mAndroidContext, e))
                    onResponse.onFailure(volleyError);
            }
        };

        VolleyRequest req = new VolleyRequest(request, onVolleyResponse, onVolleyErrorResponse);
        mRequestQueue.add(req);
    }

    public <T> Observable<T> invokeObservable(final T rpcObject) {
        final PublishSubject<T> subject = PublishSubject.create();

        invokeAsync(rpcObject, new OnResponse<T>() {
            @Override
            public void onResponse(T responseBody) {
                subject.onNext(responseBody);
                subject.onCompleted();
            }

            @Override
            public void onFailure(Throwable why) {
                subject.onError(why);
            }
        });

        return subject.asObservable();
    }

}
