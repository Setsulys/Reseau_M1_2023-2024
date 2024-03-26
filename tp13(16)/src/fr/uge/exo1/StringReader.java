package fr.uge.exo1;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String>{

	private enum State{
		DONE,WAITINGINT,WAITINGSTRING,ERROR
	}

	private State state = State.WAITINGINT;
	private static final int BUFFER_SIZE = 1024;
	private final ByteBuffer internalBuffer = ByteBuffer.allocate(BUFFER_SIZE);
	private String stringValue;
	private Charset UTF8 = StandardCharsets.UTF_8;
	private IntReader intReader = new IntReader();
	private int size;
	
	@Override
	public ProcessStatus process(ByteBuffer buffer) {
		if(state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if(state == State.WAITINGINT) {
			var reader = intReader.process(buffer);
			if(reader  == ProcessStatus.REFILL) {
				return ProcessStatus.REFILL;
			}
			else {
				size = intReader.get();
				if(size < 0 || size > BUFFER_SIZE) {
					state = State.ERROR;
					return ProcessStatus.ERROR;
				}
				state = State.WAITINGSTRING;
			}
		}
		
		if(state == State.WAITINGSTRING) {
			buffer.flip();
			var missing = size -internalBuffer.position();
			try {
				if(buffer.remaining() <= missing) {
					internalBuffer.put(buffer);
				}else {
					var oldLimit  = buffer.limit();
					buffer.limit(buffer.position() + missing);
					internalBuffer.put(buffer);
					buffer.limit(oldLimit);
				}
			}finally {
				buffer.compact();
			}
			if(internalBuffer.position() < size) {
				return ProcessStatus.REFILL;
			}
			state = State.DONE;
			internalBuffer.flip();
			stringValue = UTF8.decode(internalBuffer).toString();
		}
		return ProcessStatus.DONE;
	}

	@Override
	public String get() {
		if(state!=State.DONE) {
			throw new IllegalStateException();
		}
		return stringValue;
	}

	@Override
	public void reset() {
		state = State.WAITINGINT;
		intReader.reset();
		internalBuffer.clear();
		
	}
}
