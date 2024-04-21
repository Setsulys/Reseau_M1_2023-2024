package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message>{
	
	enum State{
		DONE,WAITING_LOGIN,WAITING_MESSAGE,ERROR
	}
	private final StringReader reader = new StringReader();
	private State state= State.WAITING_LOGIN;
	private String login;
	private String txt;
	private Message message;
	
	@Override
	public ProcessStatus process(ByteBuffer bb) {
		// TODO Auto-generated method stub
		switch(state) {
		case WAITING_LOGIN:{
			var readStatus = reader.process(bb);
			if(readStatus== ProcessStatus.ERROR) {
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}
			if(readStatus==ProcessStatus.REFILL) {
				return ProcessStatus.REFILL;
			}
			login = reader.get();
			reader.reset();
			state = State.WAITING_MESSAGE;
		}
		case WAITING_MESSAGE:{
			var readStatus = reader.process(bb);
			if(readStatus== ProcessStatus.ERROR) {
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}
			if(readStatus==ProcessStatus.REFILL) {
				return ProcessStatus.REFILL;
			}
			txt = reader.get();
			state = State.DONE;	
			break;
		}
		default:{
			throw new IllegalStateException();
		}
		}
		message = new Message(login,txt);
		return ProcessStatus.DONE;
	}

	@Override
	public Message get() {
		if(state != State.DONE) {
			throw new IllegalStateException();
		}
		return message;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		state = State.WAITING_LOGIN;
		reader.reset();
	}

}
