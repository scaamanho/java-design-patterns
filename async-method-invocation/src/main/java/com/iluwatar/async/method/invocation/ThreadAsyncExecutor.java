package com.iluwatar.async.method.invocation;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * Implementation of async executor that creates a new thread for every task.
 * 
 */
public class ThreadAsyncExecutor implements AsyncExecutor {

  /** Index for thread naming */
  private final AtomicInteger idx = new AtomicInteger(0);

  @Override
  public <T> AsyncResult<T> startProcess(Callable<T> task) {
    return startProcess(task, null);
  }

  @Override
  public <T> AsyncResult<T> startProcess(Callable<T> task, AsyncCallback<T> callback) {
    CompletableResult<T> result = new CompletableResult<>(callback);
    new Thread(() -> {
      try {
        result.setValue(task.call());
      } catch (Exception ex) {
        result.setException(ex);
      }
    }, "executor-" + idx.incrementAndGet()).start();
    return result;
  }

  @Override
  public <T> T endProcess(AsyncResult<T> asyncResult) throws ExecutionException,
      InterruptedException {
    if (asyncResult.isCompleted()) {
      return asyncResult.getValue();
    } else {
      asyncResult.await();
      return asyncResult.getValue();
    }
  }

  /**
   * Simple implementation of async result that allows completing it successfully with a value or
   * exceptionally with an exception. A really simplified version from its real life cousins
   * FutureTask and CompletableFuture.
   *
   * @see java.util.concurrent.FutureTask
   * @see java.util.concurrent.CompletableFuture
   */
  private static class CompletableResult<T> implements AsyncResult<T> {

    static final int RUNNING = 1;
    static final int FAILED = 2;
    static final int COMPLETED = 3;

    final Object lock;
    final Optional<AsyncCallback<T>> callback;

    volatile int state = RUNNING;
    T value;
    Exception exception;

    CompletableResult(AsyncCallback<T> callback) {
      this.lock = new Object();
      this.callback = Optional.ofNullable(callback);
    }

    /**
     * Sets the value from successful execution and executes callback if available. Notifies any
     * thread waiting for completion.
     *
     * @param value value of the evaluated task
     */
    void setValue(T value) {
      this.value = value;
      this.state = COMPLETED;
      this.callback.ifPresent(ac -> ac.onComplete(value, Optional.<Exception>empty()));
      synchronized (lock) {
        lock.notifyAll();
      }
    }

    /**
     * Sets the exception from failed execution and executes callback if available. Notifies any
     * thread waiting for completion.
     *
     * @param exception exception of the failed task
     */
    void setException(Exception exception) {
      this.exception = exception;
      this.state = FAILED;
      this.callback.ifPresent(ac -> ac.onComplete(null, Optional.of(exception)));
      synchronized (lock) {
        lock.notifyAll();
      }
    }

    @Override
    public boolean isCompleted() {
      return (state > RUNNING);
    }

    @Override
    public T getValue() throws ExecutionException {
      if (state == COMPLETED) {
        return value;
      } else if (state == FAILED) {
        throw new ExecutionException(exception);
      } else {
        throw new IllegalStateException("Execution not completed yet");
      }
    }

    @Override
    public void await() throws InterruptedException {
      synchronized (lock) {
        if (!isCompleted()) {
          lock.wait();
        }
      }
    }
  }
}
