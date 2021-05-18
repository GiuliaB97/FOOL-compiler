package compiler;

import java.util.*;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import compiler.AST.*;
import compiler.FOOLParser.*;
import compiler.lib.*;
import static compiler.lib.FOOLlib.*;
/**
 * Class responsible for the generation of the AST from the ST created from ANTLR.
 * It creates an abstract representation of the tree, where the useless tokens are not present
 * It throw away the syntactic sugar used by programmers to define the order in which the ST
 * should be build.
 * For each useful node of the ST this visitor try to create the corresponding node in the AST.
 * NB For the declarations before returning the new node it uses the old node to set the field
 * representing the line in which the declaration of the expression appears.
 */
public class ASTGenerationSTVisitor extends FOOLBaseVisitor<Node> {

	String indent;
	public boolean print;

	ASTGenerationSTVisitor() {}
	ASTGenerationSTVisitor(boolean debug) { print=debug; }
	/**
	 * Method that responsible of the generation of a 'prog-let-in' node.
	 */
	@Override
	public Node visitLetInProg(LetInProgContext c) {//OO
		if (print) printVarAndProdName(c);
		List<DecNode> declist = new ArrayList<>();
		for (CldecContext cldec : c.cldec()) declist.add((DecNode) visit(cldec));// OO: cldec->class declaration; in our layout the class the declaration are the first one to be declared; therefore if they are present they are at the top of the declaration section.
		for (DecContext dec : c.dec()) declist.add((DecNode) visit(dec));//other declarations of the environment section (variables, functions, etc.).
		return new ProgLetInNode(declist, visit(c.exp()));//once all the declarations have been processed the "let section" is ended and the "in section" must be processed.
	}

	/**
	 * Method that responsible of the generation of a 'prog' node.
	 */
	@Override
	public Node visitNoDecProg(NoDecProgContext c) {
		if (print) printVarAndProdName(c);
		return new ProgNode(visit(c.exp()));//since there is no "let section" (there are not declarations), it processes only the "in section" .
	}
	
	/**
	 * Method that handles the generation of the print instruction.
	 */
	@Override
	public Node visitPrint(PrintContext c) {
		if (print) printVarAndProdName(c);
		return new PrintNode(visit(c.exp()));
	}

	private void printVarAndProdName(ParserRuleContext ctx) {
		String prefix="";
		Class<?> ctxClass=ctx.getClass(), parentClass=ctxClass.getSuperclass();
		if (!parentClass.equals(ParserRuleContext.class)) // parentClass is the var context (and not ctxClass itself)
			prefix=lowerizeFirstChar(extractCtxName(parentClass.getName()))+": production #";
		System.out.println(indent+prefix+lowerizeFirstChar(extractCtxName(ctxClass.getName())));
	}

	@Override
	public Node visit(ParseTree t) {
		if (t==null) return null;
		String temp=indent;
		indent=(indent==null)?"":indent+"  ";
		Node result = super.visit(t);
		indent=temp;
		return result;
	}

	@Override
	public Node visitProg(ProgContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.progbody());
	}

	/**
	 * Method that responsible for the generation of 'if-then-else' node.
	 * It returns a new node initialized with the result of the visit on its three expressions.
	 * NB b
	 * Before returning it, it uses the 'if expression' to properly set the field corresponding to the expression declaration in the new node.
	 */
	@Override
	public Node visitIf(IfContext c) {
		if (print) printVarAndProdName(c);
		Node ifNode = visit(c.exp(0));
		Node thenNode = visit(c.exp(1));
		Node elseNode = visit(c.exp(2));
		Node n = new IfNode(ifNode, thenNode, elseNode);
		n.setLine(c.IF().getSymbol().getLine());
		return n;
	}
	/**
	 * Method responsible for the generation of a parameter node.
	 * 
	 */
	@Override
	public Node visitPars(ParsContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.exp());				//the only thing it must do is to visit its expression, to initialize the node with the correct value.
	}
	
	/**
	 * Method responsible for the generation of an 'identifier' node.
	 */
	@Override
	public Node visitId(IdContext c) {
		if (print) printVarAndProdName(c);
		Node n = new IdNode(c.ID().getText());
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}

	//EXPRESSIONS
	/**
	 * Method that handles the generation of the '+' and '-' node.
	 */
	@Override
	public Node visitPlusMinus(PlusMinusContext c) {//LE: merged plus and minus
		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.PLUS() != null) {									
			n = new PlusNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.PLUS().getSymbol().getLine());
		} else if(c.MINUS() != null){
			n = new MinusNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.MINUS().getSymbol().getLine());
		}
		return n;
	}
	/**
	 * Method that handles the generation of the times and divisions node.
	 */
	@Override
	public Node visitTimesDiv(TimesDivContext c) {//LE: merged times & div
		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.TIMES() != null) {
			n = new TimesNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.TIMES().getSymbol().getLine());
		} else if(c.DIV() != null){
			n = new DivNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.DIV().getSymbol().getLine());
		}
		return n;
	}

	/**
	 * Method that handles the generation of the '==', '>=' and '<=' node.
	 */
	@Override
	public Node visitComp(CompContext c) {//LE: merged <=, >=, ==
		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.EQ() != null) {
			n = new EqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.EQ().getSymbol().getLine());
		} else if(c.GE() != null) {
			n = new GreaterEqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.GE().getSymbol().getLine());

		} else if(c.LE() != null) {
			n = new LessEqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.LE().getSymbol().getLine());
		}
		return n;
	}

	/**
	 * Method that handles the generation of the '!' node.
	 *
	 */
	@Override
	public Node visitNot(NotContext c) {//LE
		if (print) printVarAndProdName(c);
		Node n = new NotNode(visit(c.exp())); // It creates the new node initializing it with expression.
		n.setLine(c.NOT().getSymbol().getLine());//	Before returning the node it checks its declaration line in the new node fields.
		return n;
	}

	/**
	 * Method that handles the generation of the '&&' and '||' node.
	 */
	@Override
	public Node visitAndOr(AndOrContext c) {//LE: && & !! are new, but they are handled just as: *, /, -, +
		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.AND() != null) {
			n = new AndNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.AND().getSymbol().getLine());
		} else if(c.OR() != null){
			n = new OrNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.OR().getSymbol().getLine());
		}
		return n;
	}

	/**
	 * Method responsible of the generation of a 'bool' node.
	 * In particular, this one initialize the node with a 'true' value.
	 */
	@Override
	public Node visitTrue(TrueContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(true);
	}

	/**
	 * Method responsible of the generation of a 'bool' node.
	 * In particular, this one initialize the node with a 'false' value.
	 */
	@Override
	public Node visitFalse(FalseContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(false);
	}

	/**
	 * Method responsible of the generation of a integer node.
	 * In particular, this one initialize the node with its value
	 * (checking if it is positive or not).
	 */
	@Override
	public Node visitInteger(IntegerContext c) {
		if (print) printVarAndProdName(c);
		int v = Integer.parseInt(c.NUM().getText());
		return new IntNode(c.MINUS()==null?v:-v);
	}

	/**
	 * Method responsible of the generation of an empty node.
	 */
	@Override
	public Node visitNull(NullContext c) {
		if (print) printVarAndProdName(c);
		Node n = new EmptyNode();
		return n;
	}

	/**
	 * Method that generates a 'call' node (invocation of function or a method within the class). 
	 */

	@Override
	public Node visitCall(CallContext c) {
		if (print) printVarAndProdName(c);
		List<Node> arglist = new ArrayList<>();
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		Node n = new CallNode(c.ID().getText(), arglist);
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}

	/**
	 * Method that handle a 'method call'.
	 * Method called each time a method is called with ID.ID2() notation.
	 */
	@Override
	public Node visitDotCall(DotCallContext c) {//(ID1.ID2(par1,pars2))
		if (print) printVarAndProdName(c);
		List<Node> argList = new ArrayList<>();
		for (ExpContext arg : c.exp()) argList.add(visit(arg));
		Node n = new ClassCallNode(c.ID(0).getText(), //classID-->retrieve the name of the class on which the method is calle.
				c.ID(1).getText(), //methodID: name of the method called (name  not label)
				argList);		//argument list passed to the method
		n.setLine(c.ID(0).getSymbol().getLine());
		return n;
	}

	/**
	 * Method that handle an object instantiation.
	 */
	@Override
	public Node visitNew(NewContext c) {
		if (print) printVarAndProdName(c);
		List<Node> argList = new ArrayList<>();
		for (ExpContext arg : c.exp()) argList.add(visit(arg));
		Node n = new NewNode(c.ID().getText(), argList);
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}

	//DECLARATIONS
	/**
	 * Method responsible for the generation of  the declaration of a function.
	 */
		@Override
		public Node visitFundec(FundecContext c) {
			if (print) printVarAndProdName(c);
			List<ParNode> parList = new ArrayList<>();
			for (int i = 1; i < c.ID().size(); i++) {
				ParNode p = new ParNode(c.ID(i).getText(),(TypeNode) visit(c.hotype(i-1)));//HO: type (i) -> hotype(i-1)
				p.setLine(c.ID(i).getSymbol().getLine());
				parList.add(p);
			}
			List<DecNode> decList = new ArrayList<>();
			for (DecContext dec : c.dec()) decList.add((DecNode) visit(dec));
			Node n = null;
			if (c.ID().size()>0) { //non-incomplete ST
				n = new FunNode(c.ID(0).getText(),(TypeNode)visit(c.type()),parList,decList,visit(c.exp()));
				n.setLine(c.FUN().getSymbol().getLine());
			}
			return n;
		}

		@Override
		public Node visitVardec(VardecContext c) {//OO
			if (print) printVarAndProdName(c);
			Node n = null;
			if (c.ID()!=null) { //non-incomplete ST
				n = new VarNode(c.ID().getText(), (TypeNode) visit(c.hotype()), visit(c.exp()));// HO: type -> hoType()
				n.setLine(c.VAR().getSymbol().getLine());
			}
			return n;
		}

		/**
		 * Method that handles a class' declaration.
		 */
		@Override
		public Node visitCldec(CldecContext c) {//OO
			if (print) printVarAndProdName(c);
			String superClassId=null;
			String classId= c.ID(0).getText();
			if(c.EXTENDS() != null) {			//It checks if the class extends (layout changes if it does so);
				superClassId=c.ID(1).getText();
			}
			List<FieldNode> argumentDecl = new ArrayList<>();
			List<MethodNode> methodDecl = new ArrayList<>();
			
			for(int i=0; i < c.type().size(); i++) {		//next it visits its declarations in order to retrieve their type and it to a list, it creates a new Field node for each of them and set their field line.
				TypeNode type = (TypeNode)visit(c.type(i));
				FieldNode f;
				if(superClassId!= null) {// if the class extends the ID of the fields starts from 2 instead of 1; because in position 0 there is the class ID, while in position 1 there is the superclass ID
					 f = new FieldNode(c.ID( i+2).getText(), type);
				}else {
					f = new FieldNode(c.ID(i+1).getText(), type);
				}
				
				f.setLine(c.ID(i).getSymbol().getLine());
				argumentDecl.add(f);
			}
			
			for(int i=0; i < c.methdec().size(); i++) {				//Then, it visits its method and add them to a new list (the visitMethdec handles the generation of the new Method node)
				MethodNode m = (MethodNode)visit(c.methdec(i));
				methodDecl.add(m);
			}
			
			Node n= new ClassNode(classId, argumentDecl, methodDecl, superClassId);//the last argument it is the id of the superclass

			n.setLine(c.CLASS().getSymbol().getLine());//Finally, it creates the new class node and sets its line.
			return n;
		}
		
		/**
		 * Method responsible for the generation of  the declaration of a method.
		 */
		@Override
		public Node visitMethdec(MethdecContext c) {// == funnode
			if (print) printVarAndProdName(c);
			List<ParNode> parList = new ArrayList<>();
			for (int i = 1; i < c.ID().size(); i++) {
				ParNode p = new ParNode(c.ID(i).getText(),(TypeNode) visit(c.hotype(i-1)));
				p.setLine(c.ID(i).getSymbol().getLine());
				parList.add(p);
			}
			List<DecNode> decList = new ArrayList<>();
			for (DecContext dec : c.dec()) decList.add((DecNode) visit(dec));
			Node n = null;
			if (c.ID().size()>0) { //non-incomplete ST
				n = new MethodNode( c.ID(0).getText(), (TypeNode)visit(c.type()), parList, decList, visit(c.exp()) );
				n.setLine(c.FUN().getSymbol().getLine());
			}
			return n;
		}

	//TYPES

	@Override
	public Node visitIntType(IntTypeContext c) {
		if (print) printVarAndProdName(c);
		return new IntTypeNode();
	}

	@Override
	public Node visitBoolType(BoolTypeContext c) {
		if (print) printVarAndProdName(c);
		return new BoolTypeNode();
	}

	@Override
	public Node visitIdType(IdTypeContext c) {
		if (print) printVarAndProdName(c);
		RefTypeNode n = new RefTypeNode(c.ID().getText());
		return n;
	}

	@Override
	public Node visitArrow(ArrowContext c) { // HO hotype: (hotype,hotype)->type: LPAR (hotype (COMMA hotype)* )? RPAR ARROW type ;  
		if (print) printVarAndProdName(c); //hotype: type| arrowtype

		List<TypeNode> parTypeList = new ArrayList<>();	 
		for (int i = 0; i < c.hotype().size(); i++)  	 //(hotype1, hotype2, ...)
			parTypeList.add((TypeNode) visit(c.hotype(i)));

		Node n = new ArrowTypeNode(parTypeList, (TypeNode)visit(c.type()));  
		n.setLine(c.ARROW().getSymbol().getLine());
		return n;
	}
}
