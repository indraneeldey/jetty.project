package org.eclipse.jetty.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import static org.eclipse.jetty.io.DoneIOFuture.COMPLETE;

public class AsyncByteArrayEndPoint extends ByteArrayEndPoint implements AsyncEndPoint
{
    public static final Logger LOG=Log.getLogger(AsyncByteArrayEndPoint.class);

    private final Lock _lock = new ReentrantLock();
    private volatile boolean _idlecheck;

    private volatile AbstractAsyncConnection _connection;

    private DispatchingIOFuture _readFuture;
    private DispatchingIOFuture _writeFuture;

    private ByteBuffer[] _writeBuffers;


    public AbstractAsyncConnection getAsyncConnection()
    {
        return _connection;
    }

    protected void dispatch(Runnable task)
    {
        new Thread(task).start();
    }

    public void setAsyncConnection(AbstractAsyncConnection connection)
    {
        _connection=connection;
    }

    @Override
    public IOFuture readable() throws IllegalStateException
    {
        _lock.lock();
        try
        {
            if (_readFuture!=null && !_readFuture.isDone())
                throw new IllegalStateException("previous read not complete");

            _readFuture=new ReadFuture();

            // TODO

            return _readFuture;
        }
        finally
        {
            _lock.unlock();
        }
    }

    @Override
    public IOFuture write(ByteBuffer... buffers) throws IllegalStateException
    {
        _lock.lock();
        try
        {
            if (_writeFuture!=null && !_writeFuture.isDone())
                throw new IllegalStateException("previous write not complete");

            flush(buffers);

            // Are we complete?
            for (ByteBuffer b : buffers)
            {
                if (b.hasRemaining())
                {
                    _writeBuffers=buffers;
                    _writeFuture=new WriteFuture();
                    // TODO
                    return _writeFuture;
                }
            }
            return COMPLETE;
        }
        catch(IOException e)
        {
            return new DoneIOFuture(e);
        }
        finally
        {
            _lock.unlock();
        }
    }

    /* ------------------------------------------------------------ */
    private void completeWrite()
    {
        try
        {
            flush(_writeBuffers);

            // Are we complete?
            for (ByteBuffer b : _writeBuffers)
            {
                if (b.hasRemaining())
                {
                    // TODO
                    return;
                }
            }
            // we are complete and ready
            _writeFuture.complete();
        }
        catch(final IOException e)
        {
            _writeBuffers=null;
            if (!_writeFuture.isDone())
                _writeFuture.fail(e);
        }


    }

    /* ------------------------------------------------------------ */
    @Override
    public void setCheckForIdle(boolean check)
    {
        _idlecheck=check;
    }

    /* ------------------------------------------------------------ */
    @Override
    public boolean isCheckForIdle()
    {
        return _idlecheck;
    }




    /* ------------------------------------------------------------ */
    public void checkForIdleOrReadWriteTimeout(long now)
    {
        if (_idlecheck || !_readFuture.isDone() || !_writeFuture.isDone())
        {
            long idleTimestamp=getIdleTimestamp();
            long max_idle_time=getMaxIdleTime();

            if (idleTimestamp!=0 && max_idle_time>0)
            {
                long idleForMs=now-idleTimestamp;

                if (idleForMs>max_idle_time)
                {
                    _lock.lock();
                    try
                    {
                        if (_idlecheck)
                            _connection.onIdleExpired(idleForMs);
                        if (!_readFuture.isDone())
                            _readFuture.fail(new TimeoutException());
                        if (!_writeFuture.isDone())
                            _writeFuture.fail(new TimeoutException());

                        notIdle();
                    }
                    finally
                    {
                        _lock.unlock();
                    }
                }
            }
        }
    }


    private final class WriteFuture extends DispatchingIOFuture
    {
        private WriteFuture()
        {
            super(_lock);
        }

        @Override
        protected void dispatch(Runnable task)
        {
            AsyncByteArrayEndPoint.this.dispatch(task);
        }

        @Override
        public void cancel()
        {
            _lock.lock();
            try
            {
                // TODO
                cancelled();
            }
            finally
            {
                _lock.unlock();
            }
        }
    }




    private final class ReadFuture extends DispatchingIOFuture
    {
        private ReadFuture()
        {
            super(_lock);
        }

        @Override
        protected void dispatch(Runnable task)
        {
            AsyncByteArrayEndPoint.this.dispatch(task);
        }

        @Override
        public void cancel()
        {
            _lock.lock();
            try
            {
                // TODO ??
                cancelled();
            }
            finally
            {
                _lock.unlock();
            }
        }
    }


}
