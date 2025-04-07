@param org.mglaezer.jtellmservice.Tutorial.StanzaInstructions stanzaInstructions

- Stanza idea: ${stanzaInstructions.stanzaIdea()}
  @if(stanzaInstructions.okToDeviate())
  (You may creatively adapt this idea if it improves the poem)
  @else
  (Please adhere closely to this idea)
  @endif