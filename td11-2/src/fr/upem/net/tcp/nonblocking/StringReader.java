package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String>{

	enum State{
		DONE,WAITING_SIZE,WAITING_STRING,ERROR
	}
	private static final int BUFFER_SIZE=1024;
	private final ByteBuffer internalBuffer = ByteBuffer.allocate(BUFFER_SIZE);
	private final IntReader sizeReader = new IntReader();
	private int size;
	private String msg;
	private State state = State.WAITING_SIZE;
	
	@Override
	public ProcessStatus process(ByteBuffer bb) {
		// TODO Auto-generated method stub
		switch(state) {
		case WAITING_SIZE:{
			var sizeStatus = sizeReader.process(bb);
			if(sizeStatus == ProcessStatus.ERROR) {
				state= State.ERROR;
				return ProcessStatus.ERROR;
			}
			if(sizeStatus == ProcessStatus.REFILL) {
				return ProcessStatus.REFILL;
			}
			size = sizeReader.get();
			if(size < 0 || size > BUFFER_SIZE) {
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}
			state = State.WAITING_STRING;
		}
		case WAITING_STRING:{
			bb.flip();
			try {
				var missing = size - internalBuffer.position();
				if(missing >= bb.remaining()){
					internalBuffer.put(bb);
				}
				else {
					var oldLimit = bb.limit();
					bb.limit(missing);
					internalBuffer.put(bb);
					bb.limit(oldLimit);
				}
				if(internalBuffer.position() < size) {
					return ProcessStatus.REFILL;
				}
				state = State.DONE;
				msg = StandardCharsets.UTF_8.decode(internalBuffer.flip()).toString();
				
			}finally {
				bb.compact();
			}
			break;
		}
		default:{
			throw new IllegalStateException();
		}
		}
		return ProcessStatus.DONE;
	}

	@Override
	public String get() {
		if(state !=State.DONE) {
			throw new IllegalStateException();
		}
		return msg;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		state = State.WAITING_SIZE;
		internalBuffer.clear();
		sizeReader.reset();
	}

}
