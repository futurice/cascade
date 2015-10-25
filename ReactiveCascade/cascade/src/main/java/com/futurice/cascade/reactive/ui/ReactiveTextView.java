/*
This file is part of Reactive Cascade which is released under The MIT License.
See license.txt or http://reactivecascade.com for details.
This is open source for the common good. Please contribute improvements by pull request or contact paul.houghton@futurice.com
*/
package com.futurice.cascade.reactive.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.widget.TextView;

import com.futurice.cascade.active.ImmutableValue;
import com.futurice.cascade.i.INamed;
import com.futurice.cascade.i.NotCallOrigin;
import com.futurice.cascade.reactive.IReactiveSource;
import com.futurice.cascade.reactive.ReactiveValue;

import static com.futurice.cascade.Async.UI;
import static com.futurice.cascade.Async.assertNotNull;
import static com.futurice.cascade.Async.originAsync;
import static com.futurice.cascade.Async.vv;

/**
 * Change the displayed value value any thread by calling <code>view.bindable.set(String value)</code>
 * <p>
 * Examples:
 * <pre><code>
 *     AtomicTextView view = new AtomicTextView(Activity.this);
 * <p>
 *     // Inject shared concurrent data value your model
 *     AtomicValue<String> statusAtomicValue = new AtomicValue<>();
 *     AtomicTextView liveView = new AtomicTextView(Activity.this, statusAtomicValue);
 *     statusAtomicView.set("Something updated live to the screen");
 * </code></pre>
 */
@NotCallOrigin
public class ReactiveTextView extends TextView implements INamed {
    @Nullable
    private final ImmutableValue<String> mOrigin = isInEditMode() ? null : originAsync();
    private IReactiveSource<String> mReactiveSource; // Access only value UI thread
    private volatile ReactiveValue<String> mReactiveValue = isInEditMode() ? null : new ReactiveValue<>(getName(), ""); // Change only value UI thread

    public ReactiveTextView(@NonNull  final Context context) {
        super(context);

        mReactiveValue.set(getText().toString());
    }

    public ReactiveTextView(
            @NonNull  final Context context,
            @NonNull  final AttributeSet attrs) {
        super(context, attrs);

        mReactiveValue.set(getText().toString());
    }

    public ReactiveTextView(
            @NonNull  final Context context,
            @NonNull  final AttributeSet attrs,
            @StyleRes final int defStyle) {
        super(context, attrs, defStyle);

        mReactiveValue.set(getText().toString());
    }

    @TargetApi(21)
    public ReactiveTextView(
            @NonNull  final Context context,
            @NonNull  final AttributeSet attrs,
            @AttrRes final int defStyleAttr,
            @StyleRes final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mReactiveValue.set(getText().toString());
    }

    @NonNull
    public String getName() {
        return "ReactiveEditText" + getId();
    }

    @Override // View
    @UiThread
    public void onDetachedFromWindow() {
        if (mReactiveSource != null) {
            mReactiveValue.unsubscribeSource("onDetachedFromWindow", mReactiveSource);
            mReactiveSource = null;
        }
        super.onDetachedFromWindow();
    }

    @Override // View
    @UiThread
    public void onAttachedToWindow() {
        onDetachedFromWindow();
        subscribe();
        super.onAttachedToWindow();
    }

    private void subscribe() {
        mReactiveSource = mReactiveValue.subscribe(UI, this::setText);
    }

    /**
     * Change the view model used to set the display
     *
     * @param reactiveValue the new view model
     * @param fire          push the current value of the view model to the screen after this action completes on the UI thread
     */
    public void setReactiveValue(@NonNull  final ReactiveValue<String> reactiveValue, final boolean fire) {
        assertNotNull(mOrigin);
        final String s = "setReactiveValue(" + reactiveValue.getName() + ")";

        UI.execute(() -> {
            if (mReactiveSource != null) {
                reactiveValue.unsubscribeSource(s, mReactiveSource);
            }
            vv(this, mOrigin, s);
            this.mReactiveValue = reactiveValue;
            subscribe();
            if (fire) {
                reactiveValue.fire();
            }
        });
    }

    @NonNull
    public ReactiveValue<String> getReactiveValue() {
        return this.mReactiveValue;
    }
}
