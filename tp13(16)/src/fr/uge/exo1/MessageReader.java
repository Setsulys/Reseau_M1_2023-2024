package fr.uge.exo1;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message>{

	private enum State {
		DONE, WAITINGLOGIN,WAITINGMESSAGE, ERROR
	};

	private State state = State.WAITINGLOGIN;
	private Message loginMessage;
	private StringReader reader = new StringReader();
	private String login;
	private String message;

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		// TODO Auto-generated method stub
		if(state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		try {
			switch(state) {
			case WAITINGLOGIN: 
			{
				var loginR = reader.process(bb);
				if(loginR == ProcessStatus.REFILL) {
					return ProcessStatus.REFILL;
				}
				if(loginR == ProcessStatus.ERROR) {
					state = State.ERROR;
					return ProcessStatus.ERROR;
				}
				state = State.WAITINGMESSAGE;
				login = reader.get();
				reader.reset();
			}
			case WAITINGMESSAGE:
			{
				var messageR = reader.process(bb);
				if(messageR == ProcessStatus.REFILL) {
					return ProcessStatus.REFILL;
				}
				if(messageR == ProcessStatus.ERROR) {
					state = State.ERROR;
					return ProcessStatus.ERROR;
				}
				message = reader.get();
				break;
			}
			default:{
				throw new IllegalStateException();
			}
			}


		}finally {
			bb.compact();
		}
		state = State.DONE;
		loginMessage = new Message(login, message);
		return ProcessStatus.DONE;
	}

	@Override
	public Message get() {
		if(state!=State.DONE) {
			throw new IllegalStateException();
		}
		return loginMessage;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		state =State.WAITINGLOGIN;
		reader.reset();
	}

}
