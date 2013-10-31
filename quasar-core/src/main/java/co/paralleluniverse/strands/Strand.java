/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (C) 2013, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.strands;

import co.paralleluniverse.common.util.Exceptions;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FibersMonitor;
import co.paralleluniverse.fibers.NoopFibersMonitor;
import co.paralleluniverse.fibers.SuspendExecution;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

/**
 * A Strand is either a Thread or a Fiber
 *
 * @author pron
 */
public abstract class Strand {
    public static Strand of(Object owner) {
        if (owner instanceof Strand)
            return (Strand) owner;
        if (owner instanceof Fiber)
            return (Fiber) owner;
        else
            return of((Thread) owner);
    }

    /**
     * Returns a strand representing the given thread.
     */
    public static Strand of(Thread thread) {
        return new ThreadStrand(thread);
    }

    /**
     * Returns a strand representing the given fiber.
     * The current implementation simply returns the fiber itself as {@code Fiber} extends {@code Fiber}.
     */
    public static Strand of(Fiber fiber) {
        return fiber;
    }

    /**
     * A strand's running state
     */
    public static enum State {
        /**
         * Strand created but not started
         */
        NEW,
        /**
         * Strand started but not yet running.
         */
        STARTED,
        /**
         * Strand is running.
         */
        RUNNING,
        /**
         * Strand is blocked.
         */
        WAITING,
        /**
         * Strand has terminated.
         */
        TERMINATED
    };

    /**
     * Tests whether this strand is a fiber.
     *
     * @return {@code true} iff this strand is a fiber.
     */
    public abstract boolean isFiber();

    /**
     * Returns the underlying object of this strand, namely a {@code Thread} or a {@code Fiber}.
     */
    public abstract Object getUnderlying();

    /**
     * Returns the strand's name.
     *
     * @return The strand's name. May be {@code null}.
     */
    public abstract String getName();

    /**
     * Tests whether this strand is alive, namely it has been started but not yet terminated.
     */
    public abstract boolean isAlive();

    /**
     * Tests whether this strand has terminated.
     */
    public abstract boolean isTerminated();

    /**
     * Starts the strand.
     *
     * @return {@code this}
     * @throws IllegalThreadStateException if the strand has already been started
     */
    public abstract Strand start();

    /**
     * Awaits the termination of this strand.
     * This method blocks until this strand terminates.
     *
     * @throws ExecutionException if this strand has terminated as a result of an uncaught exception
     * (which will be the {@link Throwable#getCause() cause} of the thrown {@code ExecutionException}.
     * @throws InterruptedException
     */
    public abstract void join() throws ExecutionException, InterruptedException;

    /**
     * Awaits the termination of this strand, at most for the timeout duration specified.
     * This method blocks until this strand terminates or the timeout elapses.
     *
     * @param timeout the maximum duration to wait for the strand to terminate in the time unit specified by {@code unit}.
     * @param unit the time unit of {@code timeout}.
     *
     * @throws TimeoutException if this strand did not terminate by the time the timeout has elapsed.
     * @throws ExecutionException if this strand has terminated as a result of an uncaught exception
     * (which will be the {@link Throwable#getCause() cause} of the thrown {@code ExecutionException}.
     * @throws InterruptedException
     */
    public abstract void join(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException;

    /**
     * Interrupts this strand.
     *
     * If this strand is blocked, the blocking function will throw an {@link InterruptedException}.
     * Otherwise, the strand may test its interrupted status with the {@link #interrupted()} or {@link #isInterrupted()} method.
     */
    public abstract void interrupt();

    /**
     * Tests whether this strand has been interrupted.
     *
     * @return
     * @see #interrupt()
     * @see #interrupted()
     */
    public abstract boolean isInterrupted();

    /**
     * Makes available the permit for this strand, if it
     * was not already available. If this strand was blocked on
     * {@link #park} then it will unblock. Otherwise, its next call
     * to {@link #park} is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * strand has not been started.
     */
    public abstract void unpark();

    /**
     * Makes available the permit for this strand, if it
     * was not already available. If this strand was blocked on
     * {@link #park} then it will unblock. Otherwise, its next call
     * to {@link #park} is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * strand has not been started.
     *
     * @param unblocker the synchronization object responsible for this strand unparking
     */
    public abstract void unpark(Object unblocker);

    /**
     * Returns the blocker object supplied to the most recent
     * invocation of a {@link #park(java.lang.Object) park} method that has not yet unblocked, or null
     * if not blocked. The value returned is just a momentary
     * snapshot -- the thread may have since unblocked or blocked on a
     * different blocker object.
     *
     * @return the blocker
     */
    public abstract Object getBlocker();

    /**
     * Returns the strand's current running state.
     */
    public abstract State getState();

    /**
     * Returns an array of stack trace elements representing the stack dump
     * of this strand. This method will return a zero-length array if
     * this strand has not started, has started but has not yet been
     * scheduled to run by the system, or has terminated.
     * If the returned array is of non-zero length then the first element of
     * the array represents the top of the stack, which is the most recent
     * method invocation in the sequence. The last element of the array
     * represents the bottom of the stack, which is the least recent method
     * invocation in the sequence.
     *
     * <p>Some virtual machines may, under some circumstances, omit one
     * or more stack frames from the stack trace. In the extreme case,
     * a virtual machine that has no stack trace information concerning
     * this strand is permitted to return a zero-length array from this
     * method.
     *
     * @return an array of {@link StackTraceElement}s, each represents one stack frame.
     */
    public abstract StackTraceElement[] getStackTrace();

    /**
     * Returns the strand's id.
     * Id's are unique within a single JVM instance.
     */
    public abstract long getId();

    public abstract FibersMonitor getMonitor();

    /**
     * Returns the current strand.
     * This method will return a strand representing the fiber calling this method, or the current thread if this method is not
     * called within a fiber.
     *
     * @return A strand representing the current fiber or thread
     */
    public static Strand currentStrand() {
        final Fiber fiber = Fiber.currentFiber();
        if (fiber != null)
            return of(fiber);
        else
            return of(Thread.currentThread());
    }

    /**
     * Tests whether this function is called within a fiber.
     *
     * @return {@code true} iff the code that called this method is executing in a fiber.
     */
    public static boolean isCurrentFiber() {
        return Fiber.currentFiber() != null;
    }

    /**
     * Tests whether the current strand has been interrupted. The
     * <i>interrupted status</i> of the strand is cleared by this method. In
     * other words, if this method were to be called twice in succession, the
     * second call would return {@code false} (unless the current strand were
     * interrupted again, after the first call had cleared its interrupted
     * status and before the second call had examined it).
     *
     * @return {@code true} if the current thread has been interrupted; {@code false} otherwise.
     * @see #interrupt()
     * @see #isInterrupted()
     */
    public static boolean interrupted() {
        if (Fiber.currentFiber() != null)
            return Fiber.interrupted();
        else
            return Thread.interrupted();
    }

    /**
     * Awaits the termination of a given strand.
     * This method blocks until this strand terminates.
     *
     * @param strand the strand to join. May be an object of type {@code Strand}, {@code Fiber} or {@code Thread}.
     * @param timeout the maximum duration to wait for the strand to terminate in the time unit specified by {@code unit}.
     * @param unit the time unit of {@code timeout}.
     *
     * @throws ExecutionException if this strand has terminated as a result of an uncaught exception
     * (which will be the {@link Throwable#getCause() cause} of the thrown {@code ExecutionException}.
     * @throws InterruptedException
     */
    public static void join(Object strand) throws ExecutionException, InterruptedException {
        if (strand instanceof Strand)
            ((Strand) strand).join();
        else if (strand instanceof Thread)
            ((Thread) strand).join();
        else
            throw new IllegalArgumentException("Can't join an object of type " + strand.getClass());
    }

    /**
     * Awaits the termination of a given strand, at most for the timeout duration specified.
     * This method blocks until this strand terminates or the timeout elapses.
     *
     * @param strand the strand to join. May be an object of type {@code Strand}, {@code Fiber} or {@code Thread}.
     * @param timeout the maximum duration to wait for the strand to terminate in the time unit specified by {@code unit}.
     * @param unit the time unit of {@code timeout}.
     *
     * @throws TimeoutException if this strand did not terminate by the time the timeout has elapsed.
     * @throws ExecutionException if this strand has terminated as a result of an uncaught exception
     * (which will be the {@link Throwable#getCause() cause} of the thrown {@code ExecutionException}.
     * @throws InterruptedException
     */
    public static void join(Object strand, long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        if (strand instanceof Strand)
            ((Strand) strand).join(timeout, unit);
        else if (strand instanceof Thread)
            join(Strand.of(strand), timeout, unit);
        else
            throw new IllegalArgumentException("Can't join an object of type " + strand.getClass());
    }

    /**
     * A hint to the scheduler that the current strand is willing to yield
     * its current use of a processor. The scheduler is free to ignore this
     * hint.
     *
     * <p> Yield is a heuristic attempt to improve relative progression
     * between strands that would otherwise over-utilise a CPU. Its use
     * should be combined with detailed profiling and benchmarking to
     * ensure that it actually has the desired effect.
     */
    public static void yield() throws SuspendExecution {
        if (Fiber.currentFiber() != null)
            Fiber.yield();
        else
            Thread.yield();
    }

    /**
     * Causes the currently executing strand to sleep (temporarily cease
     * execution) for the specified number of milliseconds, subject to
     * the precision and accuracy of system timers and schedulers.
     *
     * @param millis the length of time to sleep in milliseconds
     *
     * @throws IllegalArgumentException if the value of {@code millis} is negative
     * @throws InterruptedException if any strand has interrupted the current strand. The
     * <i>interrupted status</i> of the current strand is
     * cleared when this exception is thrown.
     */
    public static void sleep(long millis) throws SuspendExecution, InterruptedException {
        if (Fiber.currentFiber() != null)
            Fiber.sleep(millis);
        else
            Thread.sleep(millis);
    }

    /**
     * Causes the currently executing strand to sleep (temporarily cease
     * execution) for the specified number of milliseconds plus the specified
     * number of nanoseconds, subject to the precision and accuracy of system
     * timers and schedulers.
     *
     * @param millis the length of time to sleep in milliseconds
     * @param nanos {@code 0-999999} additional nanoseconds to sleep
     *
     * @throws IllegalArgumentException if the value of {@code millis} is negative,
     * or the value of {@code nanos} is not in the range {@code 0-999999}
     * @throws InterruptedException if any strand has interrupted the current strand. The
     * <i>interrupted status</i> of the current strand is
     * cleared when this exception is thrown.
     */
    public static void sleep(long millis, int nanos) throws SuspendExecution, InterruptedException {
        if (Fiber.currentFiber() != null)
            Fiber.sleep(millis, nanos);
        else
            Thread.sleep(millis, nanos);
    }

    /**
     * Causes the currently executing strand to sleep (temporarily cease
     * execution) for the specified duration, subject to
     * the precision and accuracy of system timers and schedulers.
     *
     * @param duration the length of time to sleep in the time unit specified by {@code unit}.
     * @param unit the time unit of {@code duration}.
     *
     * @throws InterruptedException if any strand has interrupted the current strand. The
     * <i>interrupted status</i> of the current strand is
     * cleared when this exception is thrown.
     */
    public static void sleep(long duration, TimeUnit unit) throws SuspendExecution, InterruptedException {
        if (Fiber.currentFiber() != null)
            Fiber.sleep(duration, unit);
        else
            unit.sleep(duration);
    }

    /**
     * Disables the current strand for scheduling purposes unless the
     * permit is available.
     *
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise
     * the current strand becomes disabled for scheduling
     * purposes and lies dormant until one of three things happens:
     *
     * <ul>
     * <li>Some other strand invokes {@link #unpark unpark} with the
     * current strand as the target; or
     *
     * <li>Some other strand {@link #interrupt interrupts}
     * the current strand; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the strand to park in the first place. Callers may also determine,
     * for example, the interrupt status of the strand upon return.
     */
    public static void park() throws SuspendExecution {
        if (Fiber.currentFiber() != null)
            Fiber.park();
        else
            LockSupport.park();
    }

    /**
     * Disables the current strand for scheduling purposes unless the
     * permit is available.
     *
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise
     * the current strand becomes disabled for scheduling
     * purposes and lies dormant until one of three things happens:
     *
     * <ul>
     * <li>Some other strand invokes {@link #unpark unpark} with the
     * current strand as the target; or
     *
     * <li>Some other strand {@link #interrupt interrupts}
     * the current strand; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the strand to park in the first place. Callers may also determine,
     * for example, the interrupt status of the strand upon return.
     *
     * @param blocker the synchronization object responsible for this strand parking
     */
    public static void park(Object blocker) throws SuspendExecution {
        if (Fiber.currentFiber() != null)
            Fiber.park(blocker);
        else
            LockSupport.park(blocker);
    }

    /**
     * Disables the current strand for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current strand becomes disabled
     * for scheduling purposes and lies dormant until one of four
     * things happens:
     *
     * <ul>
     * <li>Some other strand invokes {@link #unpark unpark} with the
     * current strand as the target; or
     *
     * <li>Some other strand {@link #interrupt interrupts}
     * the current strand; or
     *
     * <li>The specified waiting time elapses; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the strand to park in the first place. Callers may also determine,
     * for example, the interrupt status of the strand, or the elapsed time
     * upon return.
     *
     * @param nanos the maximum number of nanoseconds to wait
     */
    public static void parkNanos(long nanos) throws SuspendExecution {
        if (Fiber.currentFiber() != null)
            Fiber.park(nanos, TimeUnit.NANOSECONDS);
        else
            LockSupport.parkNanos(nanos);
    }

    /**
     * Disables the current strand for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current strand becomes disabled
     * for scheduling purposes and lies dormant until one of four
     * things happens:
     *
     * <ul>
     * <li>Some other strand invokes {@link #unpark unpark} with the
     * current strand as the target; or
     *
     * <li>Some other strand {@link #interrupt interrupts}
     * the current strand; or
     *
     * <li>The specified waiting time elapses; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the strand to park in the first place. Callers may also determine,
     * for example, the interrupt status of the strand, or the elapsed time
     * upon return.
     *
     * @param blocker the synchronization object responsible for this strand parking
     * @param nanos the maximum number of nanoseconds to wait
     */
    public static void parkNanos(Object blocker, long nanos) throws SuspendExecution {
        if (Fiber.currentFiber() != null)
            Fiber.park(blocker, nanos, TimeUnit.NANOSECONDS);
        else
            LockSupport.parkNanos(blocker, nanos);
    }

    /**
     * Disables the current strand for scheduling purposes, until
     * the specified deadline, unless the permit is available.
     *
     * <p>If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current strand becomes disabled
     * for scheduling purposes and lies dormant until one of four
     * things happens:
     *
     * <ul>
     * <li>Some other strand invokes {@link #unpark unpark} with the
     * current strand as the target; or
     *
     * <li>Some other strand {@link #interrupt interrupts} the
     * current strand; or
     *
     * <li>The specified deadline passes; or
     *
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     *
     * <p>This method does <em>not</em> report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the strand to park in the first place. Callers may also determine,
     * for example, the interrupt status of the strand, or the current time
     * upon return.
     *
     * @param blocker the synchronization object responsible for this strand parking
     * @param deadline the absolute time, in milliseconds from the Epoch, to wait until
     */
    public static void parkUntil(Object blocker, long deadline) throws SuspendExecution {
        if (Fiber.currentFiber() != null) {
            final long delay = deadline - System.currentTimeMillis();
            if (delay > 0)
                Fiber.park(blocker, delay, TimeUnit.MILLISECONDS);
        } else
            LockSupport.parkUntil(blocker, deadline);
    }

    /**
     * Makes available the permit for the given strand, if it
     * was not already available. If the strand was blocked on
     * {@code park} then it will unblock. Otherwise, its next call
     * to {@code park} is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * strand has not been started.
     *
     * @param strand the strand to unpark, or {@code null}, in which case this operation has no effect
     */
    public static void unpark(Strand strand) {
        if (strand != null)
            strand.unpark();
    }

    /**
     * Makes available the permit for the given strand, if it
     * was not already available. If the strand was blocked on
     * {@code park} then it will unblock. Otherwise, its next call
     * to {@code park} is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * strand has not been started.
     *
     * @param strand the strand to unpark, or {@code null}, in which case this operation has no effect
     * @param unblocker the synchronization object responsible for the strand unparking
     */
    public static void unpark(Strand strand, Object unblocker) {
        if (strand != null)
            strand.unpark(unblocker);
    }

    /**
     * Makes available the permit for the given strand, if it
     * was not already available. If the strand was blocked on
     * {@code park} then it will unblock. Otherwise, its next call
     * to {@code park} is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * strand has not been started.
     *
     * @param strand the strand to unpark, or {@code null}, in which case this operation has no effect
     */
    public static void unpark(Thread strand) {
        LockSupport.unpark((Thread) strand);
    }

    /**
     * Prints a stack trace of the current strand to the standard error stream.
     * This method is used only for debugging.
     */
    @SuppressWarnings({"CallToThreadDumpStack", "CallToPrintStackTrace"})
    public static void dumpStack() {
        if (Fiber.currentFiber() != null)
            Fiber.dumpStack();
        else
            Thread.dumpStack();
    }

    /**
     * Tests whether two strands represent the same fiber or thread.
     * @param strand1 May be an object of type {@code Strand}, {@code Fiber} or {@code Thread}.
     * @param strand2 May be an object of type {@code Strand}, {@code Fiber} or {@code Thread}.
     * @return {@code true} if the two strands represent the same fiber or the same thread; {@code false} otherwise.
     */
    public static boolean equals(Object strand1, Object strand2) {
        if (strand1 == strand2)
            return true;
        if (strand1 == null | strand2 == null)
            return false;
        return of(strand1).equals(of(strand2));
    }

    public static Strand clone(Strand strand, final SuspendableCallable<?> target) {
        if (strand.isAlive())
            throw new IllegalStateException("A strand can only be cloned after death. " + strand + " isn't dead.");
        if (strand instanceof FiberStrand)
            return clone((Fiber) strand.getUnderlying(), target);

        if (strand instanceof Fiber)
            return new Fiber((Fiber) strand, target);
        else
            return new ThreadStrand(cloneThread((Thread) strand.getUnderlying(), toRunnable(target)));
    }

    public static Strand clone(Strand strand, final SuspendableRunnable target) {
        if (strand.isAlive())
            throw new IllegalStateException("A strand can only be cloned after death. " + strand + " isn't dead.");
        if (strand instanceof FiberStrand)
            return clone((Fiber) strand.getUnderlying(), target);

        if (strand instanceof Fiber)
            return new Fiber((Fiber) strand, target);
        else
            return new ThreadStrand(cloneThread((Thread) strand.getUnderlying(), toRunnable(target)));
    }

    /**
     * A utility method that converts a {@link SuspendableRunnable} to a {@link Runnable} so that it could run
     * as the target of a thread.
     */
    public static Runnable toRunnable(final SuspendableRunnable runnable) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (SuspendExecution ex) {
                    throw new AssertionError(ex);
                } catch (InterruptedException ex) {
                }
            }
        };
    }

    /**
     * A utility method that converts a {@link SuspendableCallable} to a {@link Runnable} so that it could run
     * as the target of a thread. The return value of the callable is ignored.
     */
    public static Runnable toRunnable(final SuspendableCallable<?> callable) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    callable.run();
                } catch (SuspendExecution ex) {
                    throw new AssertionError(ex);
                } catch (InterruptedException ex) {
                } catch (Exception e) {
                    throw Exceptions.rethrow(e);
                }
            }
        };
    }

    private static Thread cloneThread(Thread thread, Runnable target) {
        Thread t = new Thread(thread.getThreadGroup(), target, thread.getName());
        t.setDaemon(thread.isDaemon());
        return t;
    }

    /**
     * This utility method prints a stack-trace into a {@link java.io.PrintStream}
     *
     * @param trace a stack trace (such as returned from {@link #getStackTrace()}.
     * @param out the {@link java.io.PrintStream} into which the stack trace will be printed.
     */
    public static void printStackTrace(StackTraceElement[] trace, java.io.PrintStream out) {
        for (StackTraceElement traceElement : trace)
            out.println("\tat " + traceElement);
    }

    /**
     * This utility method prints a stack-trace into a {@link java.io.PrintWriter}
     *
     * @param trace a stack trace (such as returned from {@link #getStackTrace()}.
     * @param out the {@link java.io.PrintWriter} into which the stack trace will be printed.
     */
    public static void printStackTrace(StackTraceElement[] trace, java.io.PrintWriter out) {
        for (StackTraceElement traceElement : trace)
            out.println("\tat " + traceElement);
    }

    private static final class ThreadStrand extends Strand {
        private final Thread thread;

        public ThreadStrand(Thread owner) {
            this.thread = owner;
        }

        @Override
        public boolean isFiber() {
            return false;
        }

        @Override
        public Thread getUnderlying() {
            return thread;
        }

        @Override
        public String getName() {
            return thread.getName();
        }

        @Override
        public long getId() {
            return thread.getId();
        }

        @Override
        public boolean isAlive() {
            return thread.isAlive();
        }

        @Override
        public boolean isTerminated() {
            return thread.getState() == Thread.State.TERMINATED;
        }

        @Override
        public State getState() {
            final Thread.State state = thread.getState();
            switch (state) {
                case NEW:
                    return State.NEW;
                case RUNNABLE:
                    return State.STARTED;
                case BLOCKED:
                case WAITING:
                case TIMED_WAITING:
                    return State.WAITING;
                case TERMINATED:
                    return State.TERMINATED;
                default:
                    throw new AssertionError("Unknown thread state: " + state);
            }
        }

        @Override
        public Strand start() {
            thread.start();
            return this;
        }

        @Override
        public void join() throws InterruptedException {
            thread.join();
        }

        @Override
        public void join(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
            long nanos = unit.toNanos(timeout);
            long millis = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);
            thread.join(millis, (int) (nanos - millis));
            if (thread.isAlive())
                throw new TimeoutException();
        }

        @Override
        public void interrupt() {
            thread.interrupt();
        }

        @Override
        public boolean isInterrupted() {
            return thread.isInterrupted();
        }

        @Override
        public void unpark() {
            LockSupport.unpark(thread);
        }

        @Override
        public void unpark(Object unblocker) {
            unpark();
        }

        @Override
        public Object getBlocker() {
            return LockSupport.getBlocker(thread);
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return thread.getStackTrace();
        }

        @Override
        public FibersMonitor getMonitor() {
            return NOOP_FIBERS_MONITOR;
        }

        @Override
        public String toString() {
            return thread.toString();
        }

        @Override
        public int hashCode() {
            return thread.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (!(obj instanceof ThreadStrand))
                return false;
            return this.thread.equals(((ThreadStrand) obj).thread);
        }
    }

    private static class FiberStrand extends Strand {
        private final Fiber fiber;

        public FiberStrand(Fiber owner) {
            this.fiber = owner;
        }

        @Override
        public boolean isFiber() {
            return true;
        }

        @Override
        public Fiber getUnderlying() {
            return fiber;
        }

        @Override
        public String getName() {
            return fiber.getName();
        }

        @Override
        public long getId() {
            return fiber.getId();
        }

        @Override
        public boolean isAlive() {
            return fiber.isAlive();
        }

        @Override
        public boolean isTerminated() {
            return fiber.isTerminated();
        }

        @Override
        public State getState() {
            return fiber.getState();
        }

        @Override
        public FibersMonitor getMonitor() {
            return fiber.getMonitor();
        }

        @Override
        public Strand start() {
            fiber.start();
            return this;
        }

        @Override
        public void join() throws ExecutionException, InterruptedException {
            fiber.join();
        }

        @Override
        public void join(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
            fiber.join(timeout, unit);
        }

        @Override
        public void interrupt() {
            fiber.interrupt();
        }

        @Override
        public boolean isInterrupted() {
            return fiber.isInterrupted();
        }

        @Override
        public void unpark() {
            fiber.unpark();
        }

        @Override
        public void unpark(Object unblocker) {
            fiber.unpark(unblocker);
        }

        @Override
        public Object getBlocker() {
            return fiber.getBlocker();
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return fiber.getStackTrace();
        }

        @Override
        public String toString() {
            return fiber.toString();
        }

        @Override
        public int hashCode() {
            return fiber.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (!(obj instanceof FiberStrand))
                return false;
            return this.fiber.equals(((FiberStrand) obj).fiber);
        }
    }
    private static final FibersMonitor NOOP_FIBERS_MONITOR = new NoopFibersMonitor();
}