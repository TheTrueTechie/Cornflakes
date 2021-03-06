package cornflakes.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class ConstructorCompiler extends Compiler implements PostCompiler {
	private ConstructorData methodData;
	private boolean write;
	private int accessor;
	private ClassData data;
	private ClassWriter cw;
	private String body;
	private String[] lines;

	public ConstructorCompiler(boolean write) {
		this.write = write;
	}

	@Override
	public void compile(ClassData data, ClassWriter cw, String body, String[] lines) {
		if (!write) {
			Compiler.addPostCompiler(data.getClassName(), this);

			this.data = data;
			this.cw = cw;
			this.body = body;
			this.lines = lines;

			String keywords = lines[0].substring(0, lines[0].indexOf("constructor")).trim();
			List<String> usedKeywords = new ArrayList<>();
			if (!keywords.isEmpty()) {
				String[] split = keywords.split(" ");
				for (String key : split) {
					key = key.trim();
					if (usedKeywords.contains(key)) {
						throw new CompileError("Duplicate keyword: " + key);
					}
					if (key.equals("public")) {
						if (usedKeywords.contains("private") || usedKeywords.contains("protected")) {
							throw new CompileError("Cannot have multiple access modifiers");
						}

						accessor |= ACC_PUBLIC;
					} else if (key.equals("private")) {
						if (usedKeywords.contains("public") || usedKeywords.contains("protected")) {
							throw new CompileError("Cannot have multiple access modifiers");
						}

						if (usedKeywords.contains("abstract")) {
							throw new CompileError("Abstract methods cannot be private");
						}

						accessor |= ACC_PRIVATE;
					} else if (key.equals("protected")) {
						if (usedKeywords.contains("private") || usedKeywords.contains("public")) {
							throw new CompileError("Cannot have multiple access modifiers");
						}

						accessor |= ACC_PROTECTED;
					} else {
						throw new CompileError("Unexpected keyword: " + key);
					}
					usedKeywords.add(key);
				}
			}

			String after = lines[0].substring(lines[0].indexOf("constructor") + "constructor".length()).trim();
			String withoutBracket = after.substring(0, after.length() - 1).trim();
			Strings.handleMatching(withoutBracket, '(', ')');

			String methodName = withoutBracket.substring(0, withoutBracket.indexOf('(')).trim();
			Strings.handleLetterString(methodName);

			if (data.hasMethod(methodName)) {
				throw new CompileError("Duplicate method: " + methodName);
			}

			String params = withoutBracket.substring(withoutBracket.indexOf('(') + 1, withoutBracket.indexOf(')'))
					.trim();
			Map<String, String> parameters = new LinkedHashMap<>();
			if (!params.isEmpty()) {
				String[] split = params.split(",");
				for (String par : split) {
					par = Strings.normalizeSpaces(par);

					String[] spl = par.split(":");
					if (spl.length == 1) {
						throw new CompileError("Parameters must have a specified type");
					} else if (spl.length > 2) {
						throw new CompileError("Unexpected symbol: " + spl[2]);
					}

					String name = spl[0].trim();
					String type = spl[1].trim();

					Strings.handleLetterString(name, Strings.VARIABLE_NAME);
					Strings.handleLetterString(type, Strings.VARIABLE_TYPE);

					if (parameters.containsKey(name)) {
						throw new CompileError("Duplicate parameter name: " + par);
					}

					String resolvedType = Types.isPrimitive(type) ? Types.getTypeSignature(type)
							: data.resolveClass(type);
					parameters.put(name, Types.padSignature(resolvedType));
				}
			}

			methodData = new ConstructorData(methodName, accessor);
			methodData.setParameters(parameters);

			data.addConstructor(methodData);
		} else {
			MethodVisitor m = cw.visitMethod(accessor, "<init>", methodData.getSignature(), null, null);
			m.visitCode();

			Label start = new Label();
			Label post = new Label();
			m.visitLabel(start);
			m.visitLineNumber(0, start);

			ConstructorBlock block = new ConstructorBlock(0, start, post);
			this.methodData.setBlock(block);

			this.methodData.addLocalVariable();
			HashMap<String, Integer> paramMap = new HashMap<>();
			for (Entry<String, String> par : methodData.getParameters().entrySet()) {
				paramMap.put(par.getKey(), this.methodData.getLocalVariables());
				this.methodData.addLocalVariable();
			}

			assignDefaults(m, data, this.methodData, block);

			String[] inner = Strings.before(Strings.after(lines, 1), 1);
			String innerBody = Strings.accumulate(inner).trim();
			String[] inner2 = Strings.accumulate(innerBody);
			GenericBodyCompiler gbc = new GenericBodyCompiler(methodData);
			gbc.compile(data, m, block, innerBody, inner2);

			if (!block.hasCalledSuper()) {
				throw new CompileError("Super must be called exactly one time before the constructor ends");
			}

			if (!gbc.returns()) {
				m.visitInsn(RETURN);
			}

			m.visitLabel(post);
			m.visitLocalVariable("this", Types.padSignature(data.getClassName()), null, start, post, 0);
			for (Entry<String, String> par : methodData.getParameters().entrySet()) {
				m.visitLocalVariable(par.getKey(), par.getValue(), null, start, post, paramMap.get(par.getKey()));
			}

			m.visitMaxs(this.methodData.getStackSize(), this.methodData.getLocalVariables());
			m.visitEnd();
		}
	}

	public void write() {
		write = true;
		compile(data, cw, body, lines);
	}

	public void compileDefault(ClassData data, ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		Label l0 = new Label();
		Label l1 = new Label();
		mv.visitLabel(l0);
		mv.visitLineNumber(0, l0);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, data.getParentName(), "<init>", "()V", false);
		MethodData mData = new ConstructorData("<init>", 0);
		assignDefaults(mv, data, mData, new ConstructorBlock(0, l0, l1));
		mv.visitInsn(RETURN);
		mv.visitLabel(l1);
		mv.visitLocalVariable("this", "L" + data.getClassName() + ";", null, l0, l1, 0);
		mv.visitMaxs(1 + mData.getStackSize(), 1);
		mv.visitEnd();

		data.addConstructor(new ConstructorData("<init>", ACC_PUBLIC));
	}

	private void assignDefaults(MethodVisitor m, ClassData data, MethodData mData, Block block) {
		for (FieldData datum : data.getFields()) {
			if (!datum.hasModifier(ACC_STATIC) && datum.getProposedData() != null) {
				m.visitVarInsn(ALOAD, 0);

				String type = datum.getType();

				if (Types.isPrimitive(type) || type.equals("Ljava/lang/String;")) {
					int push = Types.getOpcode(Types.PUSH, type);
					if (push == LDC) {
						m.visitLdcInsn(datum.getProposedData());
					} else {
						m.visitVarInsn(push, Integer.parseInt(datum.getProposedData().toString()));
					}
					mData.ics();

					m.visitFieldInsn(PUTFIELD, data.getClassName(), datum.getName(), datum.getType());
				} else {
					String raw = (String) datum.getProposedData();

					ExpressionCompiler compiler = new ExpressionCompiler(true, this.methodData);
					compiler.compile(data, m, block, raw, new String[] { raw });

					if (!Types.isSuitable(datum.getType(), compiler.getReferenceSignature())) {
						throw new CompileError(
								compiler.getReferenceSignature() + " is not assignable to " + datum.getType());
					}

					m.visitFieldInsn(PUTFIELD, data.getClassName(), datum.getName(), datum.getType());
				}
			}
		}
	}
}
