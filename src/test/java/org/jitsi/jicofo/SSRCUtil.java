/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import java.util.*;

/**
 * Utility methods for creating SSRCs and SSRC groups.
 *
 * @author Pawel Domas
 */
public class SSRCUtil
{
    static public SourcePacketExtension createSSRC(long ssrc, String[][] params)
    {
        SourcePacketExtension ssrcPE = new SourcePacketExtension();

        ssrcPE.setSSRC(ssrc);

        for (String[] param : params)
        {
            ssrcPE.addParameter(
                new ParameterPacketExtension(param[0], param[1]));
        }

        return ssrcPE;
    }

    static public SSRCGroup createSSRCGroup(String                semantics,
                                            SourcePacketExtension ssrcs[])
    {
        SourceGroupPacketExtension groupPe = new SourceGroupPacketExtension();

        groupPe.setSemantics(semantics);
        groupPe.addSources(Arrays.asList(ssrcs));

        return new SSRCGroup(groupPe);
    }
}
