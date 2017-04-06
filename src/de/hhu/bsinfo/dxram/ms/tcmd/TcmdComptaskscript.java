/*
 * Copyright (C) 2017 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science, Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.ms.tcmd;

import java.util.concurrent.Semaphore;

import de.hhu.bsinfo.dxram.ms.MasterSlaveComputeService;
import de.hhu.bsinfo.dxram.ms.TaskListener;
import de.hhu.bsinfo.dxram.ms.TaskScript;
import de.hhu.bsinfo.dxram.ms.TaskScriptState;
import de.hhu.bsinfo.dxram.term.TerminalCommand;
import de.hhu.bsinfo.dxram.term.TerminalCommandContext;

/**
 * Submit a list of tasks loaded from a file
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 03.04.2017
 */
public class TcmdComptaskscript extends TerminalCommand {
    public TcmdComptaskscript() {
        super("comptaskscript");
    }

    @Override
    public String getHelp() {
        return "Submit a list of tasks loaded from a file\n" + "Usage: comptaskscript <fileName> <cgid> [wait]\n" + "  fileName: Path to a task script file\n" +
            "  cgid: Id of the compute group to submit the task script to\n" + "  wait: Wait/block until script completed (default: true)";
    }

    @Override
    public void exec(final String[] p_args, final TerminalCommandContext p_ctx) {
        String fileName = p_ctx.getArgString(p_args, 0, null);
        short cgid = p_ctx.getArgShort(p_args, 1, (short) -1);
        boolean wait = p_ctx.getArgBoolean(p_args, 2, true);

        if (fileName == null) {
            p_ctx.printlnErr("No fileName specified");
            return;
        }

        if (cgid == -1) {
            p_ctx.printlnErr("No cgid specified");
            return;
        }

        MasterSlaveComputeService mscomp = p_ctx.getService(MasterSlaveComputeService.class);
        TaskScript taskScript = mscomp.readTaskScriptFromJsonFile(fileName);

        if (taskScript == null) {
            p_ctx.printflnErr("Reading task script from file '%s' failed", fileName);
            return;
        }

        Semaphore sem = new Semaphore(0, false);
        TaskListener listener = new TaskListener() {

            @Override
            public void taskBeforeExecution(final TaskScriptState p_taskScriptState) {
                p_ctx.printfln("ComputeTask: Starting execution %s", p_taskScriptState);
            }

            @Override
            public void taskCompleted(final TaskScriptState p_taskScriptState) {
                p_ctx.printfln("ComputeTask: Finished execution %s", p_taskScriptState);
                p_ctx.println("Return codes of slave nodes: ");
                int[] results = p_taskScriptState.getExecutionReturnCodes();

                for (int i = 0; i < results.length; i++) {
                    if (results[i] != 0) {
                        p_ctx.printflnErr("(%d): %d", i, results[i]);
                    } else {
                        p_ctx.printfln("(%d): %d", i, results[i]);
                    }
                }

                sem.release();
            }
        };

        TaskScriptState taskState = mscomp.submitTaskScript(taskScript, cgid, listener);

        if (taskState == null) {
            p_ctx.printlnErr("Task script submission failed");
            return;
        }

        p_ctx.printfln("Task script %s submitted, payload id: %d", taskScript, taskState.getTaskScriptIdAssigned());

        if (wait) {
            p_ctx.println("Waiting for task script to finish...");

            try {
                sem.acquire();
            } catch (final InterruptedException ignored) {

            }
        }
    }
}