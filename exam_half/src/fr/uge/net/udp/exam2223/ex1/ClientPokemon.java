package fr.uge.net.udp.exam2223.ex1;



import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientPokemon {

	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final Logger logger = Logger.getLogger(ClientPokemon.class.getName());
	private static final int BUFFER_SIZE = 2048;
	private final ByteBuffer sBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private final ByteBuffer rBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

	private record Pokemon(String name, Map<String,Integer> characteristics){
		public Pokemon {
			Objects.requireNonNull(name);
			characteristics= Map.copyOf(characteristics);
		}

		@Override
		public String toString() {
			var stringBuilder = new StringBuilder();
			stringBuilder.append(name);
			for( var entry : characteristics.entrySet()){
				stringBuilder.append(';')
				.append(entry.getKey())
				.append(':')
				.append(entry.getValue());
			}
			return stringBuilder.toString();
		}
	}

	private final String inFilename;
	private final String outFilename;
	private final InetSocketAddress server;
	private final DatagramChannel datagramChannel;

	public static void usage() {
		System.out.println("Usage : ClientPokemon in-filename out-filename host port ");
	}

	public ClientPokemon(String inFilename, String outFilename,
			InetSocketAddress server) throws IOException {
		this.inFilename = Objects.requireNonNull(inFilename);
		this.outFilename = Objects.requireNonNull(outFilename);
		this.server = server;
		this.datagramChannel = DatagramChannel.open();
	}


	public void launch() throws IOException, InterruptedException {
		try {
			datagramChannel.bind(null);
			// Read all lines of inFilename opened in UTF-8
			var pokemonNames = Files.readAllLines(Path.of(inFilename), UTF8);
			// List of Pokemon to write to the output file
			var pokemons = new ArrayList<Pokemon>();
			// TODO
			for(var name : pokemonNames) {
				var encoded = UTF8.encode(name);
				var size = encoded.remaining();
				sBuffer.clear();
				sBuffer.putInt(size);
				sBuffer.put(encoded);
				sBuffer.flip();
				datagramChannel.send(sBuffer, server);

				//Receiving mode
				var characteristics = new HashMap<String,Integer>();
				rBuffer.clear();
				datagramChannel.receive(rBuffer);
				rBuffer.flip();
				if(!rBuffer.hasRemaining()) {
					logger.info("no packets");
					return;
				}
				var tmpB = ByteBuffer.allocate(BUFFER_SIZE);

				var byteGet = rBuffer.get();
				while(rBuffer.hasRemaining() && byteGet !=00) {
					tmpB.put(byteGet);
					byteGet = rBuffer.get();
				}

				var nameReceived = UTF8.decode(tmpB.flip()).toString();
				tmpB.clear();

				while(rBuffer.hasRemaining()) {
					var byteChara = rBuffer.get();
					if(byteChara==00) {
						characteristics.put(UTF8.decode(tmpB.flip()).toString(),rBuffer.getInt());
						tmpB.clear();
					}
					else {
						tmpB.put(byteChara);
					}
				}
				pokemons.add(new Pokemon(nameReceived, characteristics));	

			}
			// Convert the pokemons to strings and write then in the output file
			var lines = pokemons.stream().map(Pokemon::toString).toList();
			Files.write(Paths.get(outFilename), lines , UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
		} finally {
			datagramChannel.close();
		}
	}


	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 4) {
			usage();
			return;
		}

		var inFilename = args[0];
		var outFilename = args[1];
		var server = new InetSocketAddress(args[2], Integer.parseInt(args[3]));

		// Create client with the parameters and launch it
		new ClientPokemon(inFilename, outFilename, server).launch();
	}
}