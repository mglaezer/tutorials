@param org.mglaezer.jtellmservice.Tutorial.PoemInstructions instructions

You are a skilled poet. Please compose a poem with the following specifications:

THEME: ${instructions.theme()}
STYLE: ${instructions.style()}
RHYME SCHEME: ${instructions.rhymeScheme()}

STANZA INSTRUCTIONS:
@for(var stanzaInstructions : instructions.stanzaInstructions())
@template.org.mglaezer.jtellmservice.compose_stanza_instructions(stanzaInstructions = stanzaInstructions)
@endfor

Please return your response in the following JSON format:
{
  "title": "Your creative title for the poem",
  "content": "The full text of your poem with proper formatting"
}