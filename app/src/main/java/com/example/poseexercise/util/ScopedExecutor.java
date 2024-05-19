/*
 * Copyright 2020 Google LLC. All rights reserved.
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

package com.example.poseexercise.util;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps an existing executor to provide a {@link #shutdown} method that allows subsequent
 * cancellation of submitted runnables.
 */

//Executor 인터페이스를 구현하여 특정 범위(scope) 내에서 실행할 수 있는 작업을 관리
// 작업이 특정 범위를 벗어났을 때 실행을 멈추고 싶을 때 사용

public class ScopedExecutor implements Executor {

    private final Executor executor;
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public ScopedExecutor(@NonNull Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        // Return early if this object has been shut down.
        if (shutdown.get()) { // 작업을 실행하기 전에 shutdown 플래그를 확인하여, 만약 이미 종료된 상태라면 작업을 실행하지 않고 즉시 반환
            return;
        }
        executor.execute( // 내부 executor를 사용하여 작업을 실행할 때, 다시 한 번 shutdown 상태를 확인하여, 종료된 상태이면 작업을 실행하지 않습니다.
                () -> {
                    // Check again in case it has been shut down in the mean time.
                    if (shutdown.get()) {
                        return;
                    }
                    command.run();
                });
    }

    /**
     * After this method is called, no runnables that have been submitted or are subsequently
     * submitted will start to execute, turning this executor into a no-op.
     *
     * <p>Runnables that have already started to execute will continue.
     */
    public void shutdown() {
        shutdown.set(true);
    }
}
