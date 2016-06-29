/*
This file is part of Reactive Cascade which is released under The MIT License.
See license.txt or http://reactivecascade.com for details.
This is open source for the common good. Please contribute improvements by pull request or contact paulirotta@gmail.com
*/
package com.reactivecascade.i;

import android.support.annotation.NonNull;

/**
 * A lambda-friendly functional interface for subscribe actions which receive two parameters
 *
 * @param <IN1>
 * @param <IN2>
 * @param <OUT>
 */
public interface IActionTwoR<IN1, IN2, OUT> extends IBaseAction<OUT> {
    /**
     *
     * @param in1 first input
     * @param in2 second input
     * @return
     * @throws Exception to transition to {@link com.reactivecascade.i.IAltFuture.StateError}
     * @throws java.util.concurrent.CancellationException to {@link com.reactivecascade.i.IAltFuture.StateCancelled}
     */
    OUT call(@NonNull IN1 in1,
             @NonNull IN2 in2) throws Exception;
}
