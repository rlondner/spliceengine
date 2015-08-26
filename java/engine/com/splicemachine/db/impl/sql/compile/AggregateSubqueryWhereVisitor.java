package com.splicemachine.db.impl.sql.compile;

import com.google.common.collect.Lists;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.compile.Visitable;
import com.splicemachine.db.iapi.sql.compile.Visitor;
import com.splicemachine.db.impl.ast.RSUtils;
import org.apache.log4j.Logger;
import java.util.List;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.filter;

/**
 * Encapsulates/implements current restrictions on the where-clause of subqueries in aggregate subquery flattening. We
 * consider the where-clause of a given subquery to allow flattening if:
 *
 * <pre>
 *
 * 1. The where clause is empty; or
 * 2. The root of the where clause is an AND or BRON node; and
 * 3. Correlated column references (if any) reference columns only one level up; and
 * 4. Correlated column references (if any) are part of an equality BRON; and
 * 5. Any correlated column references are not compared to subquery's the aggregated column.
 *
 * For example a subquery with this where-clause three can be flattened:
 *
 * WHERE a1=b1 AND a2=b2 AND b3>1 AND expression
 *
 *               AND
 *             /    \
 *           AND     BRON (a1=b1)
 *          /   \
 *        AND  BRON (a2=b2)
 *        /  \
 * BRON(b3>1) expression
 *
 * Where BRON = BinaryRelationalOperatorNode and expression is any uncorrelated subtree (including subqueries).
 *
 * </pre>
 * ------------------------------------------------------------- EXAMPLE 1:
 *
 * select A.* from A where a1 = (select sum(b2) from B where b1=a1)
 *
 * We can flatten this.  Becomes:
 *
 * select A.* from A join (select b1, sum(b2) s from B group by b1) foo on foo.b1 = a1 where a1 = foo.s
 *
 * ------------------------------------------------------------- EXAMPLE 2:
 *
 * select A.* from A where a1 = (select sum(b2) from B where b2=a1)
 *
 * We can NOT flatten this. When the subquery is moved to outer's from-list a1 cannot be joined with b2 which gets
 * aggregated/summed.
 *
 * ------------------------------------------------------------- EXAMPLE 3:
 *
 * select A.* from A where a1 = (select sum(b2) from B where b1 > a1)
 *
 * We can NOT flatten this. When the subquery is moved to outer's from-list the join on b1 > a1 could generate more rows
 * than exist in A.
 *
 * ------------------------------------------------------------- EXAMPLE 4:
 *
 * select A.* from A where a1 = (select sum(b2) from B where b1=a1 and b3 >= 40)
 *
 * We can flatten this. The expressions/predicates on B can be complex, or even subqueries.  As long as there are no
 * correlated column references everything gets moved to the outer's from-list.
 *
 *
 * Some of the restrictions here are fundamental and cannot be removed given our current mechanism for aggregate
 * subquery flattening. Others are limitations of the current implementation and could be removed or loosened in the
 * future.
 */
class AggregateSubqueryWhereVisitor implements Visitor {

    private static Logger LOG = Logger.getLogger(AggregateSubqueryWhereVisitor.class);

    /* The level of the subquery we are considering flattening in the enclosing predicate */
    private final int subqueryLevel;
    private final ColumnReference aggColReference;

    private boolean foundUnsupported;

    public AggregateSubqueryWhereVisitor(int subqueryLevel, ColumnReference aggColReference) {
        this.subqueryLevel = subqueryLevel;
        this.aggColReference = aggColReference;
    }

    @Override
    public Visitable visit(Visitable node, QueryTreeNode parent) throws StandardException {
        if (node instanceof AndNode) {
            return node;
        }
        if (node instanceof BinaryRelationalOperatorNode) {
            BinaryRelationalOperatorNode bro = (BinaryRelationalOperatorNode) node;
            ValueNode rightOperand = bro.getRightOperand();
            ValueNode leftOperand = bro.getLeftOperand();

            List<ColumnReference> leftReferences = RSUtils.collectNodes(leftOperand, ColumnReference.class);
            List<ColumnReference> rightReferences = RSUtils.collectNodes(rightOperand, ColumnReference.class);

            List<ColumnReference> leftReferencesCorrelated = Lists.newArrayList(filter(leftReferences, new IsCorrelatedPredicate()));
            List<ColumnReference> rightReferencesCorrelated = Lists.newArrayList(filter(rightReferences, new IsCorrelatedPredicate()));

            /* GOOD: Neither side had correlated predicates at any level. */
            if (leftReferencesCorrelated.isEmpty() && rightReferencesCorrelated.isEmpty()) {
                return node;
            }

            /* At this point we know the current BRON of the subquery where clause has correlated column references
             * on one side or the other. */

            /* BAD: Correlated predicates can only appear under an equality BRON, else we will get extra rows
             * when we convert the subquery to a join. */
            if (bro.getOperator() != RelationalOperator.EQUALS_RELOP) {
                foundUnsupported = true;
                return node;
            }

            /* BAD: We found a correlated column reference that references two or more levels up */
            CorrelationLevelPredicate correlationLevelPredicate = new CorrelationLevelPredicate(subqueryLevel);
            if (any(leftReferencesCorrelated, correlationLevelPredicate) || any(rightReferencesCorrelated, correlationLevelPredicate)) {
                foundUnsupported = true;
                return node;
            }

            /* BAD: When we have correlated predicates in the subquery they can only be CR directly under BRON for now.
             * This might look like a1*20 = b1*10, for example. */
            if (!(rightOperand instanceof ColumnReference && leftOperand instanceof ColumnReference)) {
                foundUnsupported = true;
                return node;
            }

            ColumnReference leftReference = (ColumnReference) leftOperand;
            ColumnReference rightReference = (ColumnReference) rightOperand;

            /* BAD: Correlated column reference cannot be compared to the subqueries aggregated column. */
            if (leftReference.getCorrelated() && aggColReference.equals(rightReference)
                    ||
                    rightReference.getCorrelated() && aggColReference.equals(leftReference)) {
                foundUnsupported = true;
                return node;
            }
            return node;
        }
        /* Top level node is not an AndNode or BinaryRelationalOperatorNode */
        else {
            foundUnsupported = true;
        }
        return node;
    }

    @Override
    public boolean skipChildren(Visitable node) throws StandardException {
        boolean isAndNode = node instanceof AndNode;
        return !isAndNode;
    }

    @Override
    public boolean visitChildrenFirst(Visitable node) {
        return false;
    }

    @Override
    public boolean stopTraversal() {
        return foundUnsupported;
    }

    public boolean isFoundUnsupported() {
        return foundUnsupported;
    }


    private static class IsCorrelatedPredicate implements com.google.common.base.Predicate<ColumnReference> {
        @Override
        public boolean apply(ColumnReference columnReference) {
            return columnReference.getCorrelated();
        }
    }

    /**
     * Returns true if the column reference is referring to a column MORE than one level up.
     */
    private static class CorrelationLevelPredicate implements com.google.common.base.Predicate<ColumnReference> {

        private int subqueryLevel;

        public CorrelationLevelPredicate(int subqueryLevel) {
            this.subqueryLevel = subqueryLevel;
        }

        @Override
        public boolean apply(ColumnReference columnReference) {
            return columnReference.getCorrelated() && columnReference.getSourceLevel() < subqueryLevel - 1;
        }
    }
}
