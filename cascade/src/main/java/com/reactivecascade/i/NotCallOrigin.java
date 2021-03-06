/*
This file is part of Reactive Cascade which is released under The MIT License.
See license.md , https://github.com/futurice/cascade and http://reactivecascade.com for details.
This is open source for the common good. Please contribute improvements by pull request or contact paulirotta@gmail.com
*/
package com.reactivecascade.i;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation on your classes helps the debugOrigin assist find where your async
 * operations "originated" (was created). See also
 * {@link CallOrigin} as a way to positively select classes
 * and methods of interest.
 * <p>
 * The library records a pointer to the "most interesting" method at the time an
 * {@link IAltFuture} is
 * created. Mark your class and/or method with this annotation if that part of the code
 * is for example a utility
 * that is not particularly interesting to the business logic as the runtime "originAsync"
 * of an asynchronous operation. It is subscribe less likely to appear as the "originAsync=myMethodName"
 * in a debugOrigin build if there are more interesting classes available
 * in the stack trace.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface NotCallOrigin {
}
