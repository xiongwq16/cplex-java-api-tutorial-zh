package examples;

/* -------------------------------------------------------------- -*- Java -*-
 * File: BendersATSP2.java
 * Version 12.9.0
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2000, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 *
 * Example BendersATSP2.java solves a flow MILP model for an
 * Asymmetric Traveling Salesman Problem (ATSP) instance
 * through Benders decomposition.
 *
 * The arc costs of an ATSP instance are read from an input file.
 * The flow MILP model is decomposed into a master ILP and a worker LP.
 *
 * The master ILP is then solved by adding Benders' cuts via the new generic
 * callback function benders_callback during the branch-and-cut process.
 *
 * The callback benders_callback adds to the master ILP violated Benders'
 * cuts that are found by solving the worker LP.
 *
 * The example allows the user to decide if Benders' cuts have to be separated
 * just as lazy constraints or also as user cuts. In particular:
 *
 * a) Only to separate integer infeasible solutions.
 * In this case, benders_callback is called with
 * contextid=CPX_CALLBACKCONTEXT_CANDIDATE. The current candidate integer
 * solution can be queried with CPXXcallbackgetcandidatepoint, and it can be rejected
 * by the user, optionally providing a list of lazy constraints, with the
 * function CPXXcallbackrejectcandidate.
 *
 * b) Also to separate fractional infeasible solutions.
 * In this case, benders_callback is called with
 * contextid=CPX_CALLBACKCONTEXT_RELAXATION. The current fractional solution
 * can be queried with CPXXcallbackgetrelaxationpoint. Cutting planes can then
 * be added via CPXXcallbackaddusercuts.
 *
 * The example shows how to properly support deterministic parallel search
 * with a user callback (there a significant departure here frome the legacy
 * control callbacks):
 *
 * a) To avoid race conditions (as the callback is called simultaneously by
 * multiple threads), each thread has its own working copy of the data
 * structures needed to separate cutting planes. Access to global data
 * is read-only.
 *
 * b) Thread-local data for all threads is created on THREAD_UP
 * and destroyed on THREAD_DOWN. This guarantees determinism.
 *
 * To run this example, command line arguments are required:
 *     java BendersATSP2 {0|1} [filename]
 * where
 *     0         Indicates that Benders' cuts are only used as lazy constraints,
 *               to separate integer infeasible solutions.
 *     1         Indicates that Benders' cuts are also used as user cuts,
 *               to separate fractional infeasible solutions.
 *
 *     filename  Is the name of the file containing the ATSP instance (arc costs).
 *               If filename is not specified, the instance
 *               ../../../examples/data/atsp.dat is read
 *
 *
 * ATSP instance defined on a directed graph G = (V, A)
 * - V = {0, ..., n-1}, V0 = V \ {0}
 * - A = {(i,j) : i in V, j in V, i != j }
 * - forall i in V: delta+(i) = {(i,j) in A : j in V}
 * - forall i in V: delta-(i) = {(j,i) in A : j in V}
 * - c(i,j) = traveling cost associated with (i,j) in A
 *
 * Flow MILP model
 *
 * Modeling variables:
 * forall (i,j) in A:
 *    x(i,j) = 1, if arc (i,j) is selected
 *           = 0, otherwise
 * forall k in V0, forall (i,j) in A:
 *    y(k,i,j) = flow of the commodity k through arc (i,j)
 *
 * Objective:
 * minimize sum((i,j) in A) c(i,j) * x(i,j)
 *
 * Degree constraints:
 * forall i in V: sum((i,j) in delta+(i)) x(i,j) = 1
 * forall i in V: sum((j,i) in delta-(i)) x(j,i) = 1
 *
 * Binary constraints on arc variables:
 * forall (i,j) in A: x(i,j) in {0, 1}
 *
 * Flow constraints:
 * forall k in V0, forall i in V:
 *    sum((i,j) in delta+(i)) y(k,i,j) - sum((j,i) in delta-(i)) y(k,j,i) = q(k,i)
 *    where q(k,i) =  1, if i = 0
 *                 = -1, if k == i
 *                 =  0, otherwise
 *
 * Capacity constraints:
 * forall k in V0, for all (i,j) in A: y(k,i,j) <= x(i,j)
 *
 * Nonnegativity of flow variables:
 * forall k in V0, for all (i,j) in A: y(k,i,j) >= 0
 */

import java.util.Map;
import java.util.HashMap;

import ilog.cplex.*;
import ilog.concert.*;

/**
 * deterministic parallel search of Bender's Decomposition for ATSP .<br>
 * 
 * @author xiong
 * @version v1.0
 * @since JDK1.8
 */
public final class BendersAtsp2 {

    /**
     * The BendersATSP thread-local class .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    private static final class Worker {

        private final int numNodes;
        private final int numArcs;
        private final int vNumVars;
        private final int uNumVars;
        private final IloCplex dualLpSolver;
        private final IloNumVar[] v;
        private final IloNumVar[] u;
        private final Map<IloNumVar, Integer> varMap = new HashMap<IloNumVar, Integer>();
        private IloObjective obj;

        /**
         * The constructor sets up the IloCplex algorithm to solve the worker LP, and
         * creates the worker LP (i.e., the dual of flow constraints and capacity
         * constraints of the flow MILP)
         *
         * Modeling variables:
         * forall k in V0, i in V:
         * u(k,i) = dual variable associated with flow constraint (k,i)
         *
         * forall k in V0, forall (i,j) in A:
         * v(k,i,j) = dual variable associated with capacity constraint (k,i,j)
         *
         * Objective:
         * minimize sum(k in V0) sum((i,j) in A) x(i,j) * v(k,i,j)
         * - sum(k in V0) u(k,0) + sum(k in V0) u(k,k)
         *
         * Constraints:
         * forall k in V0, forall (i,j) in A:
         * u(k,i) - u(k,j) <= v(k,i,j)
         *
         * Nonnegativity on variables v(k,i,j)
         * forall k in V0, forall (i,j) in A: v(k,i,j) >= 0
         */
        public Worker(int numNodes) throws IloException {
            this.numNodes = numNodes;
            this.numArcs = numNodes * numNodes;
            this.vNumVars = (numNodes - 1) * numArcs;
            this.uNumVars = (numNodes - 1) * numNodes;
            this.dualLpSolver = new IloCplex();
            this.v = dualLpSolver.numVarArray(vNumVars, 0.0, Double.POSITIVE_INFINITY, IloNumVarType.Float);
            this.u = dualLpSolver.numVarArray(uNumVars, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, IloNumVarType.Float);
            this.obj = dualLpSolver.minimize();

            // Set up IloCplex algorithm to solve the worker LP
            dualLpSolver.setOut(null);

            // Turn off the presolve reductions and set the CPLEX optimizer
            dualLpSolver.setParam(IloCplex.Param.Preprocessing.Reduce, 0);
            // Solve the worker LP with primal simplex method
            dualLpSolver.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);

            /*
             * Create variables v(k,i,j) forall k in V0, (i,j) in A.
             * For simplicity, also dummy variables v(k,i,i) are created.
             * Those variables are fixed to 0 and do not participate to the constraints.
             */
            for (int k = 1; k < numNodes; ++k) {
                for (int i = 0; i < numNodes; ++i) {
                    v[(k - 1) * numArcs + i * numNodes + i].setUB(0.0);
                }
            }
            dualLpSolver.add(v);

            // Set names for variables v(k,i,j)
            for (int k = 1; k < numNodes; ++k) {
                for (int i = 0; i < numNodes; ++i) {
                    for (int j = 0; j < numNodes; ++j) {
                        v[(k - 1) * numArcs + i * numNodes + j].setName(String.format("v.%d.%d.%d", k, i, j));
                    }
                }
            }
            // Associate indices to variables v(k,i,j)
            for (int j = 0; j < vNumVars; ++j) {
                varMap.put(v[j], j);
            }

            // Create variables u(k,i) forall k in V0, i in V
            dualLpSolver.add(u);

            // Set names for variables u(k,i)
            for (int k = 1; k < numNodes; ++k) {
                for (int i = 0; i < numNodes; ++i) {
                    u[(k - 1) * numNodes + i].setName(String.format("u.%d.%d", k, i));
                }
            }
            // Associate indices to variables u(k,i)
            for (int j = 0; j < uNumVars; ++j) {
                varMap.put(u[j], vNumVars + j);
            }

            // Initial objective function is empty
            dualLpSolver.add(obj);

            /*
             * Add constraints:
             * forall k in V0, forall (i,j) in A:
             * u(k,i) - u(k,j) <= v(k,i,j)
             */
            for (int k = 1; k < numNodes; ++k) {
                for (int i = 0; i < numNodes; ++i) {
                    for (int j = 0; j < numNodes; ++j) {
                        if (i != j) {
                            IloLinearNumExpr expr = dualLpSolver.linearNumExpr();
                            expr.addTerm(-1.0, v[(k - 1) * numArcs + i * (numNodes) + j]);
                            expr.addTerm(1.0, u[(k - 1) * numNodes + i]);
                            expr.addTerm(-1.0, u[(k - 1) * numNodes + j]);
                            dualLpSolver.addLe(expr, 0.0);
                        }
                    }
                }
            }
        }
        
        /**
         * This routine separates Benders' cuts violated by the current x solution.
         * Violated cuts are found by solving the worker LP. If a violated cut is found
         * then that cut is returned, otherwise <code>null</code> is returned .<br>
         * 
         * @param x
         * @param xSol
         * @return
         * @throws IloException
         */
        IloRange separate(IloNumVar[][] x, double[][] xSol) throws IloException {
            IloRange cut = null;

            /*
             * Update the objective function in the worker LP: 
             * minimize sum(k in V0) sum((i,j) in A) x(i,j) * v(k,i,j) 
             * - sum(k in V0) u(k,0) + sum(k in V0) u(k,k)
             * 
             * 注意这里采用的remove函数然后重新add,不同于直接 BendersAtsp.java 中set的方式
             */
            dualLpSolver.remove(obj);
            IloLinearNumExpr objExpr = dualLpSolver.linearNumExpr();
            for (int k = 1; k < numNodes; ++k) {
                for (int i = 0; i < numNodes; ++i) {
                    for (int j = 0; j < numNodes; ++j) {
                        objExpr.addTerm(xSol[i][j], v[(k - 1) * numArcs + i * numNodes + j]);
                    }
                }
            }
            for (int k = 1; k < numNodes; ++k) {
                objExpr.addTerm(1.0, u[(k - 1) * numNodes + k]);
                objExpr.addTerm(-1.0, u[(k - 1) * numNodes]);
            }
            obj = dualLpSolver.minimize(objExpr);
            dualLpSolver.add(obj);

            // Solve the worker LP
            dualLpSolver.solve();

            // A violated cut is available iff the solution status is Unbounded
            if (dualLpSolver.getStatus() == IloCplex.Status.Unbounded) {
                // Get the violated cut as an unbounded ray of the worker LP
                IloLinearNumExpr ray = dualLpSolver.getRay();

                /*
                 * Compute the cut from the unbounded ray. The cut is:
                 * sum((i,j) in A) (sum(k in V0) v(k,i,j)) * x(i,j)
                 * >= sum(k in V0) u(k,0) - u(k,k)
                 */
                IloLinearNumExpr cutLhs = dualLpSolver.linearNumExpr();
                double cutRhs = 0.0;

                for (IloLinearNumExprIterator it = ray.linearIterator(); it.hasNext();) {
                    IloNumVar var = it.nextNumVar();
                    double val = it.getValue();
                    
                    /*
                     * varMap中存储了v, u两类变量，前vNumVars个是v，后面的是u
                     * 使用varMap极大的提升了查找变量的速度
                     */
                    int index = varMap.get(var);

                    // vNumVars = (numNodes - 1) * numArcs
                    if (index >= vNumVars) {
                        index -= vNumVars;
                        int k = index / numNodes + 1;
                        int i = index - (k - 1) * numNodes;
                        if (i == 0) {
                            cutRhs += val;
                        }
                        else if (i == k) {
                            cutRhs -= val;
                        }
                    } else {
                        int k = index / numArcs + 1;
                        int i = (index - (k - 1) * numArcs) / numNodes;
                        int j = index - (k - 1) * numArcs - i * numNodes;
                        cutLhs.addTerm(val, x[i][j]);
                    }
                }
                cut = dualLpSolver.ge(cutLhs, cutRhs);
            }
            return cut;
        }
    }

    /**
     * 基于IloCplex.Callback.Function接口的Cut .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    private static final class BendersAtspCallback implements IloCplex.Callback.Function {
        private final IloNumVar[][] x;
        private final Worker[] workers;

        public BendersAtspCallback(IloNumVar[][] x, int numWorkers) throws IloException {
            this.x = x;
            this.workers = new Worker[numWorkers];
        }

        @Override
        public void invoke(IloCplex.Callback.Context context) throws IloException {
            int threadNo = context.getIntInfo(IloCplex.Callback.Context.Info.ThreadId);
            int numNodes = x.length;

            // setup
            if (context.inThreadUp()) {
                workers[threadNo] = new Worker(numNodes);
                return;
            }

            // teardown
            if (context.inThreadDown()) {
                workers[threadNo] = null;
                return;
            }

            // Get the current x solution
            double[][] xSol = new double[numNodes][];
            if (context.inCandidate()) {
                if (!context.isCandidatePoint()) {
                    // The model is always bounded
                    throw new IloException("Unbounded solution");
                }
                for (int i = 0; i < numNodes; ++i) {
                    xSol[i] = context.getCandidatePoint(x[i]);
                }
            } else if (context.inRelaxation()) {
                for (int i = 0; i < numNodes; ++i) {
                    xSol[i] = context.getRelaxationPoint(x[i]);
                }
            } else {
                throw new IloException("Unexpected contextID");
            }

            // Get the right worker
            Worker worker = workers[threadNo];

            // Separate cut
            IloRange violated = worker.separate(x, xSol);

            if (violated != null) {
                // Add the cut
                if (context.inCandidate()) {
                    context.rejectCandidate(violated);
                }
                else if (context.inRelaxation()) {
                    context.addUserCut(violated, IloCplex.CutManagement.UseCutPurge, false);
                } else {
                    throw new IloException("Unexpected contextID");
                }
            } else {
                System.out.println("Finish");
            }
        }
    }

    public static void main(String[] args)
            throws IloException, java.io.IOException, InputDataReader.InputDataReaderException {
        String fileName = "./data/atsp.dat";

        // Check the command line arguments
        if (args.length != 1 && args.length != 2) {
            usage();
            System.exit(-1);
        }

        if (!(args[0].equals("0") || args[0].equals("1"))) {
            usage();
            System.exit(-1);
        }

        boolean separateFracSols = Integer.parseInt(args[0]) != 0;

        final IloCplex masterIlpSolver = new IloCplex();
        try {
            masterIlpSolver.output().print("Benders' cuts separated to cut off: ");
            if (separateFracSols) {
                masterIlpSolver.output().println("Integer and fractional infeasible solutions.");
            } else {
                masterIlpSolver.output().println("Only integer infeasible solutions.");
            }

            if (args.length == 2)
                fileName = args[1];

            // Read arc_costs from data file (9 city problem)
            InputDataReader reader = new InputDataReader(fileName);
            double[][] arcCost = reader.readDoubleArrayArray();

            // create master ILP
            final int numNodes = arcCost.length;
            final IloNumVar[][] x = new IloNumVar[numNodes][];
            createMasterILP(masterIlpSolver, x, arcCost);

            int numThreads = masterIlpSolver.getNumCores();
            
            // Set up the callback to be used for separating Benders' cuts
            final BendersAtspCallback cb = new BendersAtspCallback(x, numThreads);
            long contextmask = IloCplex.Callback.Context.Id.Candidate | IloCplex.Callback.Context.Id.ThreadUp
                    | IloCplex.Callback.Context.Id.ThreadDown;
            if (separateFracSols) {
                contextmask |= IloCplex.Callback.Context.Id.Relaxation;
            }
            masterIlpSolver.use(cb, contextmask);

            // Solve the model and write out the solution
            if (masterIlpSolver.solve()) {
                IloCplex.Status solStatus = masterIlpSolver.getStatus();
                masterIlpSolver.output().println("Solution status: " + solStatus);
                masterIlpSolver.output().println("Objective value: " + masterIlpSolver.getObjValue());

                if (solStatus == IloCplex.Status.Optimal) {
                    // Write out the optimal tour
                    double[][] sol = new double[numNodes][];
                    int[] succ = new int[numNodes];
                    for (int j = 0; j < numNodes; ++j)
                        succ[j] = -1;

                    for (int i = 0; i < numNodes; i++) {
                        sol[i] = masterIlpSolver.getValues(x[i]);
                        for (int j = 0; j < numNodes; j++) {
                            if (sol[i][j] > 1e-03)
                                succ[i] = j;
                        }
                    }

                    masterIlpSolver.output().println("Optimal tour:");
                    int i = 0;
                    while (succ[i] != 0) {
                        masterIlpSolver.output().print(i + ", ");
                        i = succ[i];
                    }
                    masterIlpSolver.output().println(i);
                } else {
                    masterIlpSolver.output().println("Solution status is not Optimal");
                }
            } else {
                masterIlpSolver.output().println("No solution available");
            }
        } finally {
            masterIlpSolver.end();
        }
    }

    /**
     * Create a Master Problem
     * This method creates the master ILP (arc variables x and degree constraints).
     * 
     * Modeling variables:
     * forall (i,j) in A: x(i,j) = 1, if arc (i,j) is selected = 0, otherwise
     * 
     * Objective:
     * minimize sum((i,j) in A) c(i,j) * x(i,j)
     * 
     * Degree constraints:
     * forall i in V:
     * sum((i,j) in delta+(i)) x(i,j) = 1
     * forall i in V:
     * sum((j,i) in delta-(i)) x(j,i) = 1
     * 
     * Binary constraints on arc variables:
     * forall (i,j) in A: 
     * x(i,j) in {0, 1} .<br>
     * 
     * @param model
     * @param data
     * @param x
     * @throws IloException
     */
    private static void createMasterILP(IloCplexModeler mod, IloNumVar[][] x, double[][] arcCost) throws IloException {
        int numNodes = x.length;

        /*
         * Create variables x(i,j) for (i,j) in A
         * For simplicity, also dummy variables x(i,i) are created
         * Those variables are fixed to 0 and do not participate to the constraints
         */
        for (int i = 0; i < numNodes; ++i) {
            x[i] = mod.intVarArray(numNodes, 0, 1);
            x[i][i].setUB(0);
            for (int j = 0; j < numNodes; ++j) {
                x[i][j].setName(String.format("x.%d.%d", i, j));
            }
            mod.add(x[i]);
        }

        // Create objective function: minimize sum((i,j) in A ) c(i,j) * x(i,j)
        IloLinearNumExpr obj = mod.linearNumExpr();
        for (int i = 0; i < numNodes; ++i) {
            arcCost[i][i] = 0;
            obj.add(mod.scalProd(x[i], arcCost[i]));
        }
        mod.addMinimize(obj);

        /*
         * Add the out degree constraints
         * forall i in V: sum((i,j) in delta+(i)) x(i,j) = 1
         */
        for (int i = 0; i < numNodes; ++i) {
            IloLinearNumExpr expr = mod.linearNumExpr();
            for (int j = 0; j < i; ++j)
                expr.addTerm(1.0, x[i][j]);
            for (int j = i + 1; j < numNodes; ++j)
                expr.addTerm(1.0, x[i][j]);
            mod.addEq(expr, 1.0);
        }

        /*
         * Add the in degree constraints.
         * forall i in V: sum((j,i) in delta-(i)) x(j,i) = 1
         */
        for (int i = 0; i < numNodes; i++) {
            IloLinearNumExpr expr = mod.linearNumExpr();
            for (int j = 0; j < i; j++)
                expr.addTerm(1.0, x[j][i]);
            for (int j = i + 1; j < numNodes; j++)
                expr.addTerm(1.0, x[j][i]);
            mod.addEq(expr, 1.0);
        }
    }

    private static void usage() {
        System.err.println("Usage: java BendersATSP2 {0|1} [filename]");
        System.err.println(" 0:        Benders' cuts only used as lazy constraints,");
        System.err.println("           to separate integer infeasible solutions.");
        System.err.println(" 1:        Benders' cuts also used as user cuts,");
        System.err.println("           to separate fractional infeasible solutions.");
        System.err.println(" filename: ATSP instance file name.");
        System.err.println("           File ../../../examples/data/atsp.dat used if no name is provided.");
    }
}
