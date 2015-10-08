/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.asm;

import com.google.common.base.Throwables;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static org.objectweb.asm.Type.*;

public class Mappings {
	private static boolean IS_DEV;
	//since srg field and method names are guarranted not to collide -  we can store them in one map
	private static final Map<String, String> srgToMcp = new HashMap<>();

	static {
		String location = System.getProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp");
		IS_DEV = location != null;
		if(IS_DEV) {
			initMappings(location);
		}
	}

	//classes
	public static final String WORLD = "net/minecraft/world/World";
	public static final String VIEW_FRUSTUM = "net/minecraft/client/renderer/ViewFrustum";
	public static final String RENDER_CHUNK = "net/minecraft/client/renderer/chunk/RenderChunk";
	public static final String BLOCK_POS = "net/minecraft/util/BlockPos";

	//methods
	public static final String WORLD_IS_VALID = getNameFromSrg("func_175701_a");
	public static final String VIEW_FRUSTUM_SET_COUNT_CHUNKS = getNameFromSrg("func_178159_a");
	public static final String VIEW_FRUSTUM_GET_RENDER_CHUNK = getNameFromSrg("func_178161_a");

	//fields
	public static final String VIEW_FRUSTUM_WORLD = getNameFromSrg("field_178167_b");

	//classes referenced from asm
	public static final String WORLD_METHODS = "cubicchunks/asm/WorldMethods";
	public static final String WORLD_METHODS_IS_TALL_WORLD_DESC =
			getMethodDescriptor(getType(boolean.class), getObjectType(WORLD));
	public static final String RENDER_METHODS = "cubicchunks/asm/RenderMethods";
	public static final String RENDER_METHODS_GET_RENDER_CHUNK_DESC =
			getMethodDescriptor(getObjectType(RENDER_CHUNK), getObjectType(VIEW_FRUSTUM), getObjectType(BLOCK_POS));

	public static String getNameFromSrg(String srgName) {
		if(IS_DEV) {
			return srgToMcp.get(srgName);
		}
		return srgName;
	}

	private static void initMappings(String property) {
		try(Scanner scanner = new Scanner(new File(property))) {
			while(scanner.hasNextLine()) {
				String line = scanner.nextLine();
				parseLine(line);
			}
		} catch (FileNotFoundException e) {
			throw Throwables.propagate(e);
		}
	}

	private static void parseLine(String line) {
		if(line.startsWith("FD: ")) {
			parseField(line.substring("FD: ".length()));
		}
		if(line.startsWith("MD: ")) {
			parseMethod(line.substring("MD: ".length()));
		}
	}

	private static void parseMethod(String substring) {
		String[] s = substring.split(" ");

		final int SRG_NAME = 0, SRG_DESC = 1, MCP_NAME = 2, MCP_DESC = 3;

		int lastIndex = s[SRG_NAME].lastIndexOf('/') + 1;
		if(lastIndex < 0) lastIndex = 0;

		s[SRG_NAME] = s[SRG_NAME].substring(lastIndex);

		lastIndex = s[MCP_NAME].lastIndexOf("/") + 1;
		if(lastIndex < 0) lastIndex = 0;

		s[MCP_NAME] = s[MCP_NAME].substring(lastIndex);

		srgToMcp.put(s[SRG_NAME], s[MCP_NAME]);
	}

	private static void parseField(String str) {
		if(!str.contains(" ")) {
			return;
		}
		String[] s = str.split(" ");
		assert s.length == 2;

		int lastIndex = s[0].lastIndexOf('/') + 1;
		if(lastIndex < 0) lastIndex = 0;

		s[0] = s[0].substring(lastIndex);

		lastIndex = s[1].lastIndexOf("/") + 1;
		if(lastIndex < 0) lastIndex = 0;

		s[1] = s[1].substring(lastIndex);

		srgToMcp.put(s[0], s[1]);
	}
}
