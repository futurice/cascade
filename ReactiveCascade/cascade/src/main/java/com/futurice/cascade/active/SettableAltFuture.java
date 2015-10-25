/*
This file is part of Reactive Cascade which is released under The MIT License.
See license.txt or http://reactivecascade.com for details.
This is open source for the common good. Please contribute improvements by pull request or contact paul.houghton@futurice.com
*/
package com.futurice.cascade.active;

import android.support.annotation.CallSuper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.futurice.cascade.Async;
import com.futurice.cascade.i.CallOrigin;
import com.futurice.cascade.i.IAction;
import com.futurice.cascade.i.IActionOne;
import com.futurice.cascade.i.IActionOneR;
import com.futurice.cascade.i.IActionR;
import com.futurice.cascade.i.IActionTwoR;
import com.futurice.cascade.i.IOnErrorAction;
import com.futurice.cascade.i.IThreadType;
import com.futurice.cascade.i.NotCallOrigin;
import com.futurice.cascade.reactive.IReactiveSource;
import com.futurice.cascade.reactive.IReactiveTarget;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.futurice.cascade.Async.DEBUG;
import static com.futurice.cascade.Async.assertEqual;
import static com.futurice.cascade.Async.assertTrue;
import static com.futurice.cascade.Async.dd;
import static com.futurice.cascade.Async.ee;
import static com.futurice.cascade.Async.throwIllegalArgumentException;
import static com.futurice.cascade.Async.throwIllegalStateException;
import static com.futurice.cascade.Async.vv;

/**
 * An {@link IAltFuture} on which you can {@link SettableAltFuture#set(Object)}
 * one time to change state
 * <p>
 * Note that a <code>SettableAltFuture</code> is not itself {@link java.lang.Runnable}. You explicity {@link #set(Object)}
 * when the value is determined, and this changes the state to done. Therefore concepts like {@link IAltFuture#fork()}
 * and {@link IAltFuture#isForked()} do not have their traditional meanings.
 * <p>
 * {@link AltFuture} overrides this class.
 * TODO You may also use a {@link SettableAltFuture} to inject data where the value is determined fromKey entirely outside of the current chain hierarchy.
 * This is currently an experimental feature so be warned, your results and chain behaviour may vary. Additional
 * testing is on the long list.
 * <p>
 * You may prefer to use {@link ImmutableValue} that a similar need in some cases. That is a
 * slightly faster, simpler implementation than {@link SettableAltFuture}.
 * <p>
 * TODO Would it be helpful for debugging to store and pass forward a reference to the object which originally detected the problem? It might help with filtering what mOnFireAction you want to do mOnError
 */
@NotCallOrigin
public class SettableAltFuture<IN, OUT> implements IAltFuture<IN, OUT> {
    /**
     * A value similar to null, but meaning "no mind", "unasserted" or "state not set".
     * <p>
     * Many would use <code>null</code> instead of <code>ZEN</code> to initialize a variable. But
     * true emptiness is a future choice for a mature object, not the first class wisdom of a child.
     * The difference can matter, for example to differentiate between "the value has been set to null"
     * and "the value has not yet been set".
     * <p>
     * The contract is: once a state of ZEN has been lost, it can not be regained.
     * <p>
     * A Cup of Tea
     * <p>
     * Nan-in, a Japanese master during the Meiji era (1868-1912), received a university
     * professor who came to inquire about Zen.
     * <p>
     * Nan-in served tea. He poured his visitor's cup full, and subscribe kept on pouring.
     * The professor watched the overflow until he no longer could restrain himself.
     * "It is overfull. No more will go in! "Like this cup," Nan-in said, "you are full
     * of your own opinions and speculations. How can I show you Zen unless you first empty your cup?"
     * <p>
     * {@link "http://www.lotustemple.us/resources/koansandmondo.html"}
     * <p>
     * TODO Document ZEN and apply to use to allow collections and arguments that currently might not accept null to accept null as a first class value. Not yet used in many places.
     */
    protected static final IAltFutureState ZEN = new IAltFutureState() {
        @NonNull
        @Override
        public Exception getException() {
            throw new IllegalStateException("Can not getException() for a non-exception state ZEN");
        }

        @Override
        public String toString() {
            return "ZEN";
        }
    };

    /*
     * TODO It should be possible to refactor and eliminate the FORKED state, using only ZEN
     * This would however result in more debugging difficulty. Users would not know at they time
     * of .fork() if the operation has already been forked due to an error in their code. They
     * would only find out much later. Perhaps this is acceptable if the debug pattern remains clear
     * as to the source of the problem.
     */
    protected static final IAltFutureState FORKED = new IAltFutureState() {
        @NonNull
        @Override
        public Exception getException() {
            throw new IllegalStateException("Can not getException() for a non-exception state FORKED");
        }

        @Override
        public String toString() {
            return "FORKED";
        }
    };
    @NonNull
    protected final AtomicReference<Object> mStateAR = new AtomicReference<>(ZEN);
    @NonNull
    protected final ImmutableValue<String> mOrigin;
    @NonNull
    protected final IThreadType mThreadType;
    @NonNull
    protected final CopyOnWriteArrayList<IAltFuture<OUT, ?>> mThenAltFutureList = new CopyOnWriteArrayList<>(); // Callable split IThreadType actions to start after this mOnFireAction completes
    @Nullable
    private volatile IOnErrorAction mOnError;
    @Nullable
    private volatile IAltFuture<?, IN> mPreviousAltFuture = null;

    /**
     * Create an immutable value holder for a value which is not yet determined
     *
     * @param threadType
     */
    public SettableAltFuture(@NonNull final IThreadType threadType) {
        this.mThreadType = threadType;
        this.mOrigin = Async.originAsync();
    }

    /**
     * Create a holder and set the immutable value
     *
     * @param threadType
     * @param value
     */
    public SettableAltFuture(@NonNull final IThreadType threadType,
                             @NonNull final OUT value) {
        this(threadType);

        try {
            set(value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem initializing SettableAltFuture: " + value, e);
        }
    }

    private void assertNotForked() {
        if (Async.DEBUG && isForked()) {
            throwIllegalStateException(this, mOrigin, "You attempted to set AltFuture.mOnError() after fork() or cancel(). That is not meaningful (except as a race condition..:)");
        }
    }

    @NonNull
    private IAltFuture<IN, OUT> setOnError(@NonNull final IOnErrorAction action) {
        assertNotForked();
        assertEqual(null, this.mOnError);

        this.mOnError = action;

        return this;
    }

    @Override // IAltFuture
    @CallOrigin
    @CallSuper
    public boolean cancel(@NonNull final String reason) {
        if (mStateAR.compareAndSet(ZEN, new AltFutureStateCancelled(reason))) {
            dd(this, mOrigin, "Cancelled: reason=" + reason);
            return true;
        } else {
            final Object state = mStateAR.get();
            if (state instanceof AltFutureStateCancelled) {
                dd(this, mOrigin, "Ignoring duplicate cancel. The ignored reason=" + reason + ". The previously accepted cancellation reason=" + state);
            } else {
                dd(this, mOrigin, "Ignoring duplicate cancel. The ignored reason=" + reason + ". The previously accepted successful completion value=" + state);
            }
            return false;
        }
    }

    @Override // IAltFuture
    @CallOrigin
    public boolean cancel(@NonNull final String reason,
                          @NonNull final Exception e) {
        assertNotDone();

        final IAltFutureState errorState = new AltFutureStateError(reason, e);
        if (mStateAR.compareAndSet(ZEN, errorState)) {
            dd(this, mOrigin, "Cancelled fromKey ZEN state: reason=" + reason);
            return true;
        } else {
            if (mStateAR.compareAndSet(FORKED, errorState)) {
                dd(this, mOrigin, "Cancelled fromKey FORKED state: reason=" + reason);
                return true;
            } else {
                if (DEBUG) {
                    final Object state = mStateAR.get();
                    if (state instanceof IAltFutureStateCancelled) {
                        dd(this, mOrigin, "Ignoring duplicate cancel. The ignored reason=" + reason + " e=" + e + ". The previously accepted cancellation reason=" + state);
                    } else {
                        dd(this, mOrigin, "Ignoring duplicate cancel. The ignored reason=" + reason + " e=" + e + ". The previously accepted successful completion value=" + state);
                    }
                }

                return false;
            }
        }
    }

    @Override // IAltFuture
    public boolean isCancelled() {
        return isCancelled(mStateAR.get());
    }

    protected final boolean isCancelled(@NonNull final Object objectThatMayBeAState) {
        return objectThatMayBeAState instanceof IAltFutureStateCancelled;
    }

    @Override // IAltFuture
    public final boolean isDone() {
        return isDone(mStateAR.get());
    }

    protected boolean isDone(@NonNull final Object state) {
        return state != ZEN && state != FORKED; // && !(state instanceof AltFutureStateSetButNotYetForked);
    }

    @Override // IAltFuture
    public final boolean isForked() {
        return isForked(mStateAR.get());
    }

    protected boolean isForked(@NonNull final Object state) {
        return state != ZEN; // && !(state instanceof AltFutureStateSetButNotYetForked);
    }

    @Override // IAltFuture
    @NonNull
    public IAltFuture<IN, OUT> fork() {
        final IAltFuture<?, IN> previousAltFuture = getPreviousAltFuture();
        final Object state;

        //TODO Evaluate if this logic is no longer needed since UI.then() etc all fork() for you
        if (previousAltFuture != null && !previousAltFuture.isDone()) {
            dd(this, mOrigin, "Warning: Ignoring attempt to fork() forked/completed/cancelled/error-state AltFuture. This may be a normal race condition, or maybe you fork() multiple times. state= " + mStateAR.get());
        }
        doFork();

        return this;
    }

    protected void doFork() {
        // This is not an IRunnableAltFuture, so nothing to run(). But AltFuture overrides this and does more
        try {
            doThenActions();
        } catch (Exception e) {
            ee(this, "Can not doFork()", e);
        }
    }

    @Override // IAltFuture
    @NonNull
    public final <P> IAltFuture<IN, OUT> setPreviousAltFuture(@NonNull final IAltFuture<P, IN> altFuture) {
        assertEqual(null, mPreviousAltFuture);
        //TODO mPreviousAltFuture should be an atomicvalue with atomic set and assert only after that
        this.mPreviousAltFuture = altFuture;

        return this;
    }

    /**
     * Implementations of {@link #fork()} must call this when completed. It reduces the window of time
     * in which past intermediate calculation values in a active chain are held in memory. It is
     * the equivalent of the (illegal) statement:
     * <code>{@link #setPreviousAltFuture(IAltFuture)}</code> to null.
     * <p>
     * This may not be done until {@link #isDone()} == true, such as when the {@link #fork()} has completed.
     */
    protected final void clearPreviousAltFuture() {
        if (isDone()) {
            this.mPreviousAltFuture = null;
        }
    }

    @Override // IAltFuture
    @Nullable
    @SuppressWarnings("unchecked")
    public final <UPCHAIN_IN> IAltFuture<UPCHAIN_IN, IN> getPreviousAltFuture() {
        return (IAltFuture<UPCHAIN_IN, IN>) this.mPreviousAltFuture;
    }

    protected void assertNotDone() {
        assertTrue("assertNotDone failed: SettableFuture already finished or entered canceled/error state", !isDone());
    }

    @Override // IAltFuture
    @NonNull
    @SuppressWarnings("unchecked")
    public OUT get() {
        final Object state = mStateAR.get();

        if (!isDone(state)) {
            throwIllegalStateException(this, mOrigin, "Attempt to get() AltFuture that is not yet finished. state=" + state);
        }
        if (isCancelled(state)) {
            throwIllegalStateException(this, mOrigin, "Attempt to get() AltFuture that is cancelled: state=" + state);
        }

        return (OUT) state;
    }

    @Override // IAltFuture
    @Nullable
    @SuppressWarnings("unchecked")
    public OUT safeGet() {
        final Object state = mStateAR.get();

        if (!isDone(state) || isCancelled(state)) {
            return null;
        }

        return (OUT) state;

    }

    @Override // IAltFuture
    @NonNull
    public final IThreadType getThreadType() {
        return this.mThreadType;
    }

    @Override // IAltFuture
    public void set(@NonNull final OUT value) throws Exception {
//        if (mStateAR.compareAndSet(ZEN, value)) {
//            // Previous state was ZEN, so accept it but do not enter isDone() and complete .subscribe() mOnFireAction until after .fork() is called
//            if (DEBUG) {
//                final int n = mThenAltFutureList.size();
//                vv(this, mOrigin, "Set value= " + value + " with " + n + " downchain actions");
//            }
//            return;
//        }

        if (mStateAR.compareAndSet(ZEN, value) || mStateAR.compareAndSet(FORKED, value)) {
            // Previous state was FORKED, so set completes the mOnFireAction and continues the chain
            vv(this, mOrigin, "SettableAltFuture set, value= " + value);
            doThenActions();
            return;
        }

        // Already set, cancelled or error state
        throwIllegalStateException(this, mOrigin, "Attempted to set " + this + " to value=" + value + ", but the value can only be set once and was already set to value=" + get());
    }

    @Override // IAltFuture
    public void doThenOnCancelled(@NonNull final CancellationException cancellationException) throws Exception {
        vv(this, mOrigin, "Handling doThenOnCancelled " + mOrigin + " for reason=" + cancellationException);
        this.mStateAR.set(cancellationException);
        final IOnErrorAction oe = this.mOnError;

        if (oe != null && oe.call(cancellationException)) {
            return; // Error chain was consumed
        }
        cancelAllDownchainActions(cancellationException);
    }

    private void cancelAllDownchainActions(@NonNull final CancellationException cancellationException) throws Exception {
        forEachThen(altFuture -> {
            altFuture.doThenOnCancelled(cancellationException);
        });
    }

    /**
     * Perform some action on an instantaneous snapshot of the list of .subscribe() down-chain actions
     *
     * @param action
     * @throws Exception
     */
    private void forEachThen(@NonNull final IActionOne<IAltFuture<OUT, ?>> action) throws Exception {
        final Iterator<IAltFuture<OUT, ?>> iterator = mThenAltFutureList.iterator();

        while (iterator.hasNext()) {
            action.call(iterator.next());
        }
    }

    @NotCallOrigin
    @Override // IAltFuture
    public void doThenOnError(@NonNull final IAltFutureState state) throws Exception {
        vv(this, mOrigin, "Handling doThenOnError(): " + state);
        this.mStateAR.set(state);
        final IOnErrorAction oe = mOnError;
        boolean consumed = false;

        if (oe != null) {
            final IActionR<IN, Boolean> errorAction;
            consumed = oe.call(state.getException());
        }

        if (consumed) {
            // When an error is consumed in the chain, we switch over to still notify with cancellation instead
            cancelAllDownchainActions(new CancellationException("Up-chain consumed the following: " + state.getException().toString()));
        } else {
            forEachThen(altFuture -> {
                altFuture.doThenOnError(state);
            });
        }
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> onError(@NonNull final IOnErrorAction action) {
        setOnError(action);

        //NOTE: mOnError must return a new object to allow proper chaining of mOnError actions
        if (mPreviousAltFuture != null) {
            return new AltFuture<>(mThreadType, out -> out);
        }

        // No input argument, head of chain
        return new AltFuture<>(mThreadType, () -> {
        });
    }

    private void assertErrorState() {
        if (DEBUG && !(mStateAR.get() instanceof AltFutureStateError)) {
            throwIllegalStateException(this, mOrigin, "Do not call doThenOnError() directly. It can only be called when we are already in an error state and this is done for you when the AltFuture enters an error state by running code which throws an Exception");
        }
    }

    //----------------------------------- .then() actions ---------------------------------------------
    @NonNull
    private <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> addToThenQueue(@NonNull final IAltFuture<OUT, DOWNCHAIN_OUT> altFuture) {
        altFuture.setPreviousAltFuture(this);
        this.mThenAltFutureList.add(altFuture);
        if (isDone()) {
            vv(this, mOrigin, "Warning: an AltFuture was added as a .subscribe() mOnFireAction to an already completed AltFuture. Being aggressive, are you? It is supported but in most cases you probably want top setup your entire chain before you fork any part of it");
//            altFuture.map((IActionOne) v -> {
//                visualize(mOrigin.getName(), v.toString(), "AltFuture");
//            });
            altFuture.fork();
        }

        return altFuture;
    }

    protected void doThenActions() throws Exception {
        //vv(mOrigin, TAG, "Start doThenActions, count=" + this.mThenAltFutureList.size() + ", state=" + mStateAR.get());
        if (DEBUG && !isDone()) {
//            vv(this, mOrigin, "This AltFuture is not yet done, so can't doNextActions() yet");
            return;
        }

        if (mThenAltFutureList.isEmpty()) {
            return;
        }

        final Object state = this.mStateAR.get();
        if (state instanceof IAltFutureStateCancelled) {
            if (state instanceof AltFutureStateCancelled /*|| isConsumed(state)*/) {
                final String reason = ((AltFutureStateCancelled) state).reason;
                final CancellationException cancellationException = new CancellationException(reason);
                forEachThen(altFuture -> {
                    altFuture.doThenOnCancelled(cancellationException);
                });
            } else if (state instanceof AltFutureStateError) {
                forEachThen(altFuture -> {
                    altFuture.doThenOnError((AltFutureStateError) state);
                });
            } else {
                throw new UnsupportedOperationException("Unsupported SettableAltFuture state: " + state.getClass());
            }
        } else {
            forEachThen(af -> {
                if (af.isForked()) {
                    vv(this, mOrigin, "Potential race such as adding .subscribe() after .fork(): This is acceptable but aggressive. doThenActions() finds one of the actions chained after the current AltFuture has already been forked: " + af);
                } else {
                    af.fork();
                }
            });
        }
    }

//    @Override // IAltFuture
//    @NonNull//
//    @CheckResult(suggest = "IAltFuture#fork()")
//    @SuppressWarnings("unchecked")
//    public <DOWNCHAIN_OUT> IAltFuture<OUT, OUT> split(@NonNull  final IAltFuture<OUT, DOWNCHAIN_OUT> altFuture) {
//        then(altFuture);
//
//        return (IAltFuture<OUT, OUT>) this;
//    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull final IAltFuture<OUT, DOWNCHAIN_OUT> altFuture) {
        return addToThenQueue(altFuture);
    }

    @NonNull
    @Override
    @SafeVarargs
    public final IAltFuture<IN, OUT> join(@NonNull IActionTwoR<OUT, IN, OUT> joinAction, @NonNull IAltFuture<?, IN>... altFuturesToJoin) {
        assertTrue("join(IAltFuture...) with empty list of upchain things to join makes no sense", altFuturesToJoin.length == 0);
        assertTrue("join(IAltFuture...) with single item in the list of upchain things to join is confusing. Use .then() instead", altFuturesToJoin.length == 1);

        final IThreadType threadType = altFuturesToJoin[0].getThreadType();
        final IAltFuture<IN, OUT> outAltFuture = new SettableAltFuture<>(threadType);
        final AtomicInteger downCounter = new AtomicInteger(altFuturesToJoin.length);
        final AtomicReference<OUT> incrementalOut = new AtomicReference<>(null);

        for (final IAltFuture<?, IN> upchainAltFuture : altFuturesToJoin) {
            upchainAltFuture.then(threadType, in -> {
                while (true) {
                    final OUT initialOut = outAltFuture.get();
                    final OUT currentTryOut = joinAction.call(initialOut, upchainAltFuture.get());
                    if (incrementalOut.compareAndSet(initialOut, currentTryOut)) {
                        break;
                    }
                }
                if (downCounter.decrementAndGet() == 0) {
                    outAltFuture.set(incrementalOut.get());
                }
            }).onError(e -> {
                outAltFuture.cancel("join() function execution problem", e);
                return false;
            });
        }

        return outAltFuture;
    }

    /**
     * Continue downchain actions on the specified {@link IThreadType}
     *
     * @param theadType the thread execution group to change to for the next chain operation
     * @return the previous chain link masked to reflect the new {@link IThreadType}
     */
    @NonNull
    @Override
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> on(@NonNull IThreadType theadType) {
        return then(new SettableAltFuture<>(theadType));
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> then(@NonNull final IActionOne<OUT> action) {
        return then(mThreadType, action);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> then(@NonNull final IThreadType threadType,
                                     @NonNull final IActionOne<OUT> action) {
        return then(new AltFuture<>(threadType, action));
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull final IActionR<OUT, DOWNCHAIN_OUT> action) {
        return then(mThreadType, action);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull final IThreadType threadType,
                                                               @NonNull final IActionR<OUT, DOWNCHAIN_OUT> action) {
        return then(new AltFuture<>(threadType, action));
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull final IActionOneR<OUT, DOWNCHAIN_OUT> action) {
        return then(mThreadType, action);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull final IThreadType threadType,
                                                               @NonNull final IActionOneR<OUT, DOWNCHAIN_OUT> action) {
        return then(new AltFuture<>(threadType, action));
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> then(@NonNull final IAction<OUT> action) {
        return then(mThreadType, action);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> then(@NonNull final IThreadType threadType,
                                     @NonNull final IAction<OUT> action) {
        return then(new AltFuture<>(threadType, action));
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<List<IN>, List<OUT>> map(@NonNull final IActionOneR<IN, OUT> action) {
        return map(mThreadType, action);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<List<IN>, List<OUT>> map(@NonNull final IThreadType threadType,
                                               @NonNull final IActionOneR<IN, OUT> action) {
        return new AltFuture<>(threadType,
                (List<IN> listIN) -> {
                    //TODO Mapping is single-threaded even for long lists or complex transforms
                    //TODO Idea: create the list of things to call(), and offer that to other threads in the ThreadType if they have freetime to help out
                    final List<OUT> outputList = new ArrayList<>(listIN.size());
                    for (IN IN : listIN) {
                        outputList.add(action.call(IN));
                    }
                    return outputList;
                }
        );
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<List<IN>, List<IN>> filter(@NonNull final IActionOneR<IN, Boolean> action) {
        return filter(mThreadType, action);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<List<IN>, List<IN>> filter(
            @NonNull final IThreadType threadType,
            @NonNull final IActionOneR<IN, Boolean> action) {
        return new AltFuture<>(threadType,
                (List<IN> listIN) -> {
                    final List<IN> outputList = new ArrayList<>(listIN.size());
                    for (IN IN : listIN) {
                        if (action.call(IN)) {
                            outputList.add(IN);
                        }
                    }
                    return outputList;
                }
        );
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> set(@NonNull final IReactiveTarget<OUT> reactiveTarget) {
        return then(reactiveTarget::fire);
    }

    @NonNull
    @Override
    public IAltFuture<OUT, OUT> set(@NonNull IReactiveSource<IN> reactiveSource) {
        return null;
    }

//=============================== End .then() actions ========================================

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = "IAltFuture#fork()")
    public IAltFuture<OUT, OUT> set(@NonNull final ImmutableValue<OUT> immutableValue) {
        return then(immutableValue::set);
    }

    /**
     * This is a marker interface. If you return state information, the atomic inner state of your
     * implementation should implement this interface.
     */
    @NotCallOrigin
    protected interface IAltFutureStateCancelled extends IAltFutureState {
    }

//    /**
//     * Since SettableAltFuture.set(T) can happen _before_ .fork(), this marks the intermediate state
//     * until .fork() is explicitly called. This affects isDone() logic in particular, because in this
//     * state isDone() is not true. Only fork() makes it true.
//     * <p>
//     * Due to Java generics limitations with a non-static generic inner class and instanceof, this is better
//     * off with "Object" than type "T". Type safety is held by surrounding methods.
//     */
//    private static class AltFutureStateSetButNotYetForked implements IAltFutureState {
//        final Object value;
//
//        AltFutureStateSetButNotYetForked(Object value) {
//            this.value = value;
//        }
//
//        @NonNull//
//        @Override
//        public Exception getException() {
//            throw new IllegalStateException("Can not getException() for a non-exception state " + AltFutureStateSetButNotYetForked.class.getSimpleName());
//        }
//
//        @Override
//        public String toString() {
//            return "SET_BUT_NOT_YET_FORKED: value=" + value;
//        }
//
//    }

    @NotCallOrigin
    protected static final class AltFutureStateCancelled implements IAltFutureStateCancelled {
        final String reason;

        AltFutureStateCancelled(@NonNull String reason) {
            if (DEBUG && reason.length() == 0) {
                throwIllegalArgumentException(this, "You must specify the cancellation reason to keep debugging sane");
            }
            this.reason = reason;
            dd(this, "Moving to StateCancelled:\n" + this.reason);
        }

        @Override // IAltFutureStateCancelled
        @NonNull
        public Exception getException() {
            throw new IllegalStateException("Can not getException() for a non-exception state " + AltFutureStateCancelled.class.getSimpleName());
        }

        @Override // Object
        @NonNull
        public String toString() {
            return "CANCELLED: reason=" + reason;
        }
    }

    /**
     * An atomic state change marking also the reason for entering the exception state
     */
    @NotCallOrigin
    protected static class AltFutureStateError implements IAltFutureStateCancelled {
        @NonNull
        final String reason;
        @NonNull
        final Exception e;
        private volatile boolean consumed = false; // Set true to indicate that no more down-chain error notifications should occur, the developer asserts that the error is handled and of no further interest for all global states and down-chain listeners

        AltFutureStateError(@NonNull String reason, @NonNull Exception e) {
            this.reason = reason;
            this.e = e;
            ee(this, "Moving to StateError:\n" + this.reason, e);
        }

//        /**
//         * Note that this is not thread-safe. You may only process errors and consume them on a single thread
//         */
//        //FIXME CONTINUE HERE- consume() is not used consistently- eliminate or use everywhere
//        void consume() {
//            consumed = true;
//        }
//
//        boolean isConsumed() {
//            return consumed;
//        }

        @Override // IAltFutureStateCancelled
        @NonNull
        public Exception getException() {
            return e;
        }

        @Override // Object
        @NonNull
        public String toString() {
            return "ERROR: reason=" + reason + " error=" + e + " consumed=" + consumed;
        }
    }
}
