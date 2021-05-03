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
 * It creates an abstract implementation of tree, where the useless tokens are not present.
 *
 * For each useful node of the ST this visitor try to create the corresponding node in the AST.
 * NB For the declarations before returning the new node it uses the old node to set the field
 * representing the line in which the declaration of the expression appears.
 *
 * @author giuliabrugnatti
 *
 */
public class ASTGenerationSTVisitor extends FOOLBaseVisitor<Node> {

	String indent;
	public boolean print;

	ASTGenerationSTVisitor() {}
	ASTGenerationSTVisitor(boolean debug) { print=debug; }
	/**
	 * Method that responsible of the generation of a prog-let-in node.
	 * It visits all the declarations present in the let-in section;
	 * adding then to a list that will be use to inizialize a new prog-let-it node.
	 */
	@Override
	public Node visitLetInProg(LetInProgContext c) {//OO
		if (print) printVarAndProdName(c);
		List<DecNode> declist = new ArrayList<>();
		for (CldecContext cldec : c.cldec()) declist.add((DecNode) visit(cldec));// OO: cldec: class declaration
		for (DecContext dec : c.dec()) declist.add((DecNode) visit(dec));
		return new ProgLetInNode(declist, visit(c.exp()));
	}

	/**
	 * Method that responsible of the generation of a prog node.
	 * Since there are no declarations in the program it just returns a new
	 * prog node initialized with the visit at its body.
	 */
	@Override
	public Node visitNoDecProg(NoDecProgContext c) {
		if (print) printVarAndProdName(c);
		return new ProgNode(visit(c.exp()));
	}
	/**
	 * Method that handles the generation of the print instruction.
	 * It just returns a new Print node initialized with the visit at its expression.
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
	 * MEthod that responsible for the generation of if-then-else expression.
	 * It returns a new node initialized with the result of the visit on its three expressions.
	 * NB before returning it it uses the if expression to properly set the field corresponding to the expression declaration in the new node.
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

	@Override
	public Node visitPars(ParsContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.exp());
	}
	/**
	 * Method responsible for the generation of an identifier-
	 * It returns a new node initialized with the old node identifier and
	 * uses that latter to set the field of the new node corresponding to its line's declaration.
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
	 * Method that handles the generation of the plus and minus node.
	 * It checks which of the two node must create, then it creates it initializing it with two expressions.
	 * Before returning the node it checks its declaration line in the new node fields.
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
	 * It checks which of the two node must create, then it creates it initializing it with two expressions.
	 * Before returning the node it checks its declaration line in the new node fields.
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
	 * Method that handles the generation of the equal, greater-equal and less-equal node.
	 * It checks which of the three node must create, then it creates it initializing it with two expressions.
	 * Before returning the node it checks its declaration line in the new node fields.
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
	 * Method that handles the generation of the not node.
	 * It creates the new node initializing it with expression.
	 * Before returning the node it checks its declaration line in the new node fields.
	 */
	@Override
	public Node visitNot(NotContext c) {//LE
		if (print) printVarAndProdName(c);
		Node n = new NotNode(visit(c.exp()));
		n.setLine(c.NOT().getSymbol().getLine());
		return n;
	}

	/**
	 * Method that handles the generation of the and and or node.
	 * It checks which of the two node must create, then it creates it initializing it with two expressions.
	 * Before returning the node it checks its declaration line in the new node fields.
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
	 * Method responsible of the generation of a bool node.
	 * In particular, this one initialize the node with a true value.
	 */
	@Override
	public Node visitTrue(TrueContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(true);
	}

	/**
	 * Method responsible of the generation of a bool node.
	 * In particular, this one initialize the node with a false value.
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
	 * Method that generates a call node initializing it it with its ID, its argument list and
	 * as appens for almost all the other declarations it uses the former node to properly set the field
	 * corresponding to the line in which the function/method (called bu another method in the same class)
	 * is declared.
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
	 * Method that handle a method call ( ID1.ID2(par1,pars2)).
	 * When it initialize the node it retrieves the class ID, its ID its arguments list
	 * (which it has previously visited and added to a list),
	 * next it sets the field representing the line in which it is declared using the former node.
	 */
	@Override
	public Node visitDotCall(DotCallContext c) {
		if (print) printVarAndProdName(c);
		List<Node> argList = new ArrayList<>();
		for (ExpContext arg : c.exp()) argList.add(visit(arg));
		Node n = new ClassCallNode(c.ID(0).getText(), //classID-->refTypeNode con all'interno la classe a cui si riferisce
				c.ID(1).getText(), argList);		//arglist
		n.setLine(c.ID(0).getSymbol().getLine());
		return n;
	}

	/**
	 * Method that handle an object instantiation.
	 * When it initialize the node it retrieves the class ID and its arguments list
	 * (which it has previously visited and added to a list),
	 * next it sets the field representing the line in which it is declared using the former node.
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
	 * It musts declare also its parameters and set their line field.
	 * Next, it must handles possible declaration within the function visiting all of them.
	 * Finally it returns a function node also setting its line field.
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
		//QUI ERRORE
		@Override
		public Node visitCldec(CldecContext c) {//OO
			if (print) printVarAndProdName(c);
			String ext = c.EXTENDS() != null ?  c.ID(1).getText() : null;
			List<FieldNode> arglist = new ArrayList<>();
			List<MethodNode> methlist = new ArrayList<>();
			//visito il campo c.type per vedere tutti i campi
			for(int i=0; i < c.type().size(); i++) {
				TypeNode type = (TypeNode)visit(c.type(i));
				FieldNode f = new FieldNode(c.ID(ext == null ? i+1 : i+2).getText(), type);
				f.setLine(c.ID(i).getSymbol().getLine());
				arglist.add(f);
			}
			//visito il campo c.methdec per vedere tutte le dichiarazioni di metodi
			for(int i=0; i < c.methdec().size(); i++) {
				MethodNode m = (MethodNode)visit(c.methdec(i));
				methlist.add(m);
			}
			Node n = new ClassNode(c.ID(0).getText(), arglist, methlist, ext);
			n.setLine(c.CLASS().getSymbol().getLine());
			return n;
		}
		/**
		 * Method responsible for the generation of  the declaration of a method.
		 * It musts declare also its parameters and set their line field.
		 * Next, it must handles possible declaration within the function visiting all of them.
		 * Finally it returns a function node also setting its line field.
		 */
		@Override
		public Node visitMethdec(MethdecContext c) {// == funnode
			if (print) printVarAndProdName(c);
			List<ParNode> parList = new ArrayList<>();
			for (int i = 1; i < c.ID().size(); i++) {
				ParNode p = new ParNode(c.ID(i).getText(),
						(TypeNode) visit(c.hotype(i-1)));
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
	public Node visitArrow(ArrowContext c) { // HO hotype: (hotype,hotype)->type
		if (print) printVarAndProdName(c);

		List<TypeNode> parTypeList = new ArrayList<>();	 // carico i parametri che sono hotype (quindi ricorsivamente potrebbero anche essere di tipo arrow)
		for (int i = 0; i < c.hotype().size(); i++)  	 // attenzione: non sono parNode come su funzione ma typeNode perchè è un TIPO: la sintassi è x:(int,int)->int
			parTypeList.add((TypeNode) visit(c.hotype(i)));

		Node n = new ArrowTypeNode(parTypeList, (TypeNode)visit(c.type()));  // il ritorno è sempre type
		n.setLine(c.ARROW().getSymbol().getLine());
		return n;
	}
}
