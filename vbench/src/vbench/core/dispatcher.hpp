// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/thread.h>

namespace vbench {

template <typename T>
Dispatcher<T>::Dispatcher(Handler<T> &fallback)
    : _fallback(fallback),
      _lock(),
      _threads(),
      _closed(false)
{
}

template <typename T>
Dispatcher<T>::~Dispatcher() {}

template <typename T>
bool
Dispatcher<T>::waitForThreads(size_t threads, size_t pollCnt) const
{
    for (size_t i = 0; i < pollCnt; ++i) {
        if (i != 0) {
            vespalib::Thread::sleep(20);
        }
        {
            vespalib::LockGuard guard(_lock);
            if (_threads.size() >= threads) {
                return true;
            }
        }
    }
    return false;
}

template <typename T>
void
Dispatcher<T>::close()
{
    std::vector<ThreadState*> threads;
    {
        vespalib::LockGuard guard(_lock);
        std::swap(_threads, threads);
        _closed = true;
    }
    for (size_t i = 0; i < threads.size(); ++i) {
        threads[i]->gate.countDown();
    }
}

template <typename T>
void
Dispatcher<T>::handle(std::unique_ptr<T> obj)
{
    vespalib::LockGuard guard(_lock);
    if (!_threads.empty()) {
        ThreadState *state = _threads.back();
        _threads.pop_back();
        guard.unlock();
        state->object = std::move(obj);
        state->gate.countDown();
    } else {
        bool closed = _closed;
        guard.unlock();
        if (!closed) {
            _fallback.handle(std::move(obj));
        }
    }
}

template <typename T>
std::unique_ptr<T>
Dispatcher<T>::provide()
{
    ThreadState state;
    {
        vespalib::LockGuard guard(_lock);
        if (!_closed) {
            _threads.push_back(&state);
            guard.unlock();
            state.gate.await();
        }
    }
    return std::move(state.object);
}

} // namespace vbench
