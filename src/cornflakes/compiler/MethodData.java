package cornflakes.compiler;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

public class MethodData {
	private String name;
	private String returnType;
	private Map<String, String> parameters = new LinkedHashMap<>();
	private List<LocalData> locals = new ArrayList<>();
	private int stackSize;
	private int localVariables;
	private int modifiers;
	private int blocks;

	public static MethodData fromJavaMethod(Method method) {
		MethodData mData = new MethodData(method.getName(), Types.getTypeSignature(method.getReturnType()),
				method.getModifiers());
		Parameter[] params = method.getParameters();
		for (int i = 0; i < params.length; i++) {
			mData.addParameter(params[i].getName(), Types.getTypeSignature(params[i].getType()));
		}
		return mData;
	}

	public MethodData(String name, String ret, int mods) {
		this.name = name;
		this.returnType = ret;
		this.modifiers = mods;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getReturnTypeSignature() {
		return returnType;
	}

	public void setReturnTypeSignature(String returnType) {
		this.returnType = returnType;
	}

	public ClassData getReturnType() {
		return Types.getTypeFromSignature(Types.unpadSignature(returnType));
	}

	public int getStackSize() {
		return stackSize;
	}

	public void setStackSize(int stackSize) {
		this.stackSize = stackSize;
	}

	public int getLocalVariables() {
		return localVariables;
	}

	public void setLocalVariables(int localVariables) {
		this.localVariables = localVariables;
	}

	public void addLocalVariable() {
		this.localVariables++;
	}

	public void increaseStackSize() {
		this.stackSize++;
	}

	public boolean hasLocal(String name, Label start, Label end) {
		return getLocal(name, start, end) != null;
	}

	public LocalData getLocal(String name, Label start, Label end) {
		for (LocalData data : this.locals) {
			if(data.getName().equals(name))
				return data;
			if (data.getName().equals(name) && start.getOffset() >= data.getStart().getOffset()) {
				return data;
			}
		}
		return null;
	}

	public void addLocal(LocalData local) {
		locals.add(local);
	}

	public void setParameters(Map<String, String> params) {
		this.parameters = new LinkedHashMap<>(params);

		int idx = hasModifier(Opcodes.ACC_STATIC) ? 0 : 1;
		for (Entry<String, String> par : this.parameters.entrySet()) {
			this.locals.add(new LocalData(par.getKey(), par.getValue(), null, null, idx++, 0));
		}
	}

	public void setLabels(Label start, Label end) {
		for (LocalData local : this.locals) {
			local.setStart(start);
			local.setEnd(end);
		}
	}

	public void addParameter(String name, String type) {
		this.parameters.put(name, type);
	}

	public Map<String, String> getParameters() {
		return this.parameters;
	}

	public String getParameterType(String name) {
		return parameters.get(name);
	}

	public boolean hasParameter(String name) {
		return parameters.containsKey(name);
	}

	public int getModifiers() {
		return modifiers;
	}

	public void setModifiers(int modifiers) {
		this.modifiers = modifiers;
	}

	public boolean hasModifier(int mod) {
		return (this.modifiers & mod) == mod;
	}

	public String getSignature() {
		String desc = "(";
		for (String par : parameters.values()) {
			desc += par;
		}
		desc += ")" + getReturnTypeSignature();

		return desc;
	}

	@Override
	public boolean equals(Object obj) {
		if ((obj instanceof MethodData)) {
			MethodData data = (MethodData) obj;
			return data.toString().equals(this.toString());
		}
		return false;
	}

	@Override
	public String toString() {
		return getName() + getSignature();
	}

	public List<LocalData> getLocals() {
		return this.locals;
	}

	public int getBlocks() {
		return blocks;
	}

	public void setBlocks(int blocks) {
		this.blocks = blocks;
	}

	public void addBlock() {
		this.blocks++;
	}
}
