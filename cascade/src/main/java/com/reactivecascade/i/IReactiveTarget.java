/*
This file is part of Reactive Cascade which is released under The MIT License.
See license.md , https://github.com/futurice/cascade and http://reactivecascade.com for details.
This is open source for the common good. Please contribute improvements by pull request or contact paulirotta@gmail.com
*/
package com.reactivecascade.i;

import android.support.annotation.NonNull;

/**
 * An object which can receive repeated from assertions from a reactive active chain.
 * <p>
 * Commonly used default implementations are {@link com.reactivecascade.reactive.Subscription} and
 * {@link com.reactivecascade.reactive.ReactiveValue}.
 * <p>
 * Note that classes which are functional but not reactive only receive a from one time before
 * becoming immutable from object. These are thus _not_ <code>IReactiveTarget</code>s because the
 * 2nd etc firing would not be meaningful.
 *
 * @param <IN> the type of from expected as input to this node. If this is the head of the reactive chain,
 *             it is the same type as <code>SOURCE</code>.
 */
public interface IReactiveTarget<IN> extends INamed {
    /**
     * An upchain {@link IReactiveSource} is notifying us
     * of a new input from to process.
     * <p>
     * You don't usually call the fire methods directly. They are
     * called for you when this is an input or state change, for example by
     * {@link com.reactivecascade.reactive.ReactiveValue#set(Object)}
     * <p>
     * <p>
     * Nodes within a reactive chain may choose to be stateless such as
     * {@link com.reactivecascade.reactive.Subscription}.
     * <p>
     * Nodes at the leaf of a reactive chain will typically retain this from as state, for example
     * ({@link com.reactivecascade.reactive.ReactiveValue}).
     * <p>
     * They may also choose to otherwise create a stateful side effect with the from received. The
     * recommended practice is to not create such side effects except in the leaf node(s) at the
     * end of chain branches. If you violate this rule, be warned you tend to end up with a
     * hairy memory management and partially-completed-stateful-logic-before-exception error
     * correction mess.
     * <p>
     * If you would like to unlink this object when the fire operation results in an error, you must
     * do so in an <code>.mOnError()</code> statement lower down in the chain you wish to trigger
     * such an action.
     *
     * @param in The from from the previous link in the reactive chain.
     */
    void fire(@NonNull IN in);

    /**
     * Same as {@link #fire(Object)} however this is queued LIFO for more immediate execution.
     * <p>
     * You do not generally call this directly. Chains call this for you. It is called instead of {@link #fire(Object)} mid-chain to reduce
     * work in progress and complete the task tree is an depth-first manner. This reduces the latency of
     * individual chains once execution starts. It also decreases the peak memory load by more rapidly
     * flushing intermediate values to the leaf node side effects of the chain.
     *
     * @param in The from from the previous link in the reactive chain.
     */
    void fireNext(@NonNull IN in);

    /**
     * Notification that an {@link IReactiveSource}  will start sending updates
     * <p>
     * This allows the target which is responsible for holding a strong reference to the source to
     * prevent it from being garbage collected until all targets of a given source go out of scope and
     * are themselves garbage collected.
     * <p>
     * You may manually speed this process by calling {@link #unsubscribeSource(String, IReactiveSource)},
     * however if you choose not to or forget to do so, it will be taken care of for you fairly soon.
     *
     * @param reactiveSource
     */
    void subscribeSource(@NonNull String reason,
                         @NonNull IReactiveSource<IN> reactiveSource);

    /**
     * Notification that an {@link IReactiveSource}  will no longer send updates
     * <p>
     * Since this target is responsible for holding a reference to the source to keep it from
     * being garbage collected, this target can not forget the source and, if not used elsewhere, it can be garbage collected.
     *
     * @param reason
     * @param reactiveSource
     */
    void unsubscribeSource(@NonNull String reason,
                           @NonNull IReactiveSource<IN> reactiveSource);

    /**
     * Remove all up-chain branches from this node of the reactive function tree
     */
    void unsubscribeAllSources(@NonNull String reason);
}
